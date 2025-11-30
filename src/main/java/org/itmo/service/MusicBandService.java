package org.itmo.service;

import jakarta.persistence.EntityNotFoundException;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import org.itmo.dto.*;
import org.itmo.mapper.MusicBandMapper;
import org.itmo.model.*;
import org.itmo.repository.MusicBandRepository;
import org.itmo.repository.AlbumRepository;
import org.itmo.repository.CoordinatesRepository;
import org.itmo.repository.StudioRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.messaging.simp.SimpMessagingTemplate;

import org.springframework.security.core.context.SecurityContextHolder; 

import org.itmo.dto.MusicBandCreateDto;

import java.io.ByteArrayInputStream;
import java.io.InputStream;

import jakarta.xml.bind.JAXBContext;
import jakarta.xml.bind.Unmarshaller;
import jakarta.xml.bind.JAXBException;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.time.ZonedDateTime; 

import jakarta.validation.ValidationException;


import org.itmo.model.enums.ImportStatus;
import org.itmo.repository.ImportHistoryRepository;
import org.itmo.repository.UserRepository;
import org.itmo.service.ImportHistoryService;

// Добавьте в начало файла импорты MinIO
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import io.minio.RemoveObjectArgs;
import java.util.UUID;
import java.io.BufferedInputStream;

import io.minio.CopyObjectArgs;
import io.minio.CopySource;


@Service
@Transactional
public class MusicBandService {
    private final MusicBandRepository musicBandRepository;
    private final AlbumRepository albumRepository;
    private final CoordinatesRepository coordinatesRepository;
    private final StudioRepository studioRepository;
    
    private final MusicBandMapper musicBandMapper;
    private final SimpMessagingTemplate messagingTemplate;
    
    private final ImportHistoryRepository historyRepository;
    private final UserRepository userRepository;
    private final ImportHistoryService importHistoryService;

    // Добавьте в класс MusicBandService новые поля
    private final MinioClient minioClient;
    private final String minioBucketName;



    public MusicBandService(MusicBandRepository musicBandRepository,
                            AlbumRepository albumRepository,
                            CoordinatesRepository coordinatesRepository,
                            StudioRepository studioRepository,
                            MusicBandMapper musicBandMapper,
                            SimpMessagingTemplate messagingTemplate,

                            ImportHistoryRepository historyRepository,
                            UserRepository userRepository,
                            ImportHistoryService importHistoryService,
                            MinioClient minioClient, // <-- Новое
                            String minioBucketName) { // <-- Новое

        this.musicBandRepository = musicBandRepository;
        this.albumRepository = albumRepository;
        this.coordinatesRepository = coordinatesRepository;
        this.studioRepository = studioRepository;
        
        this.musicBandMapper = musicBandMapper;
        this.messagingTemplate = messagingTemplate;

        
        this.historyRepository = historyRepository;
        this.userRepository = userRepository;
        this.importHistoryService = importHistoryService;

        // ... существующая инициализация ...
        this.minioClient = minioClient;
        this.minioBucketName = minioBucketName;
    }


    private void notifyClients(String type) {

        messagingTemplate.convertAndSend("/topic/bands/updates", type);
    }

    public Page<MusicBandResponseDto> list(String nameEquals, Pageable pageable) {
        if (nameEquals != null && !nameEquals.isEmpty()) {
            return musicBandRepository.findByName(nameEquals, pageable)
                    .map(musicBandMapper::toResponseDto);
        }
        return musicBandRepository.findAll(pageable).map(musicBandMapper::toResponseDto);
    }

    public MusicBandResponseDto get(@NotNull Long id) {
        MusicBand musicBand = musicBandRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("MusicBand not found: " + id));
        return musicBandMapper.toResponseDto(musicBand);
    }

    public MusicBandResponseDto create(@Valid MusicBandCreateDto dto) {
        
        checkUniqueness(dto, null); 
        
        MusicBand musicBand = musicBandMapper.toEntity(dto);


        Long coordsId = dto.getCoordinates() != null ? dto.getCoordinates().getId() : null;
        if (coordsId != null) {
            Coordinates persistentCoords = coordinatesRepository.findById(coordsId)
                    .orElseThrow(() -> new EntityNotFoundException("Coordinates not found: " + coordsId));
            musicBand.setCoordinates(persistentCoords);
        } else if (dto.getCoordinates() != null) {
            Coordinates newCoords = musicBandMapper.toEntity(dto.getCoordinates());
            newCoords = coordinatesRepository.save(newCoords);
            musicBand.setCoordinates(newCoords);
        } else {
            throw new IllegalArgumentException("coordinates are required");
        }


        Long albumId = dto.getBestAlbum() != null ? dto.getBestAlbum().getId() : null;
        if (albumId != null) {
            Album persistentAlbum = albumRepository.findById(albumId)
                    .orElseThrow(() -> new EntityNotFoundException("Album not found: " + albumId));
            musicBand.setBestAlbum(persistentAlbum);
        } else if (dto.getBestAlbum() != null) {
            Album newAlbum = musicBandMapper.toEntity(dto.getBestAlbum());
            newAlbum = albumRepository.save(newAlbum);
            musicBand.setBestAlbum(newAlbum);
        }


        Long studioId = dto.getStudio() != null ? dto.getStudio().getId() : null;
        if (studioId != null) {
            Studio persistentStudio = studioRepository.findById(studioId)
                    .orElseThrow(() -> new EntityNotFoundException("Studio not found: " + studioId));
            musicBand.setStudio(persistentStudio);
        }


        
        musicBand = musicBandRepository.save(musicBand);

        
        Long realBandId = musicBand.getId();

        
        if (realBandId == null) {
            
            MusicBand persistedBand = musicBandRepository
                    .findByNameAndGenre(musicBand.getName(), musicBand.getGenre())
                    .orElseThrow(() -> new IllegalStateException("Группа была сохранена, но не найдена по Name/Genre. Критическая ошибка транзакции."));

            
            realBandId = persistedBand.getId();

            
            musicBand = persistedBand;
        }
        

        
        MusicBandResponseDto responseDto = musicBandMapper.toResponseDto(musicBand);

        
        responseDto.setId(realBandId);

        
        if (musicBand.getCoordinates() != null && responseDto.getCoordinates() != null) {
            responseDto.getCoordinates().setId(musicBand.getCoordinates().getId());
        }

        notifyClients("BAND_UPDATED");
        return responseDto;
    }

    @Transactional
    public MusicBandResponseDto update(@NotNull Long id, @Valid MusicBandCreateDto patch) {
        MusicBand existing = musicBandRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("MusicBand not found: " + id));


        
        MusicBandCreateDto futureDto = new MusicBandCreateDto();

        
        futureDto.setName(existing.getName());
        futureDto.setGenre(existing.getGenre());

        
        if (patch.getName() != null) futureDto.setName(patch.getName());
        if (patch.getGenre() != null) futureDto.setGenre(patch.getGenre());

        
        checkUniqueness(futureDto, id);
        


        if (patch.getName() != null) existing.setName(patch.getName());
        if (patch.getGenre() != null) existing.setGenre(patch.getGenre());
        if (patch.getNumberOfParticipants() > 0) existing.setNumberOfParticipants(patch.getNumberOfParticipants());
        if (patch.getSingleCount() != null) existing.setSingleCount(patch.getSingleCount());
        if (patch.getDescription() != null) existing.setDescription(patch.getDescription());
        if (patch.getAlbumCount() > 0) existing.setAlbumCount(patch.getAlbumCount());
        if (patch.getEstablishmentDate() != null) existing.setEstablishmentDate(patch.getEstablishmentDate());


        if (patch.getCoordinates() != null) {
            Long coordsId = patch.getCoordinates().getId();
            if (coordsId != null) {
                Coordinates persistentCoords = coordinatesRepository.findById(coordsId)
                        .orElseThrow(() -> new EntityNotFoundException("Coordinates not found: " + coordsId));
                existing.setCoordinates(persistentCoords);
            } else {
                Coordinates newCoords = musicBandMapper.toEntity(patch.getCoordinates());
                newCoords = coordinatesRepository.save(newCoords);
                existing.setCoordinates(newCoords);
            }
        }


        if (patch.getBestAlbum() != null) {
            Long albumId = patch.getBestAlbum().getId();
            if (albumId != null) {
                Album persistentAlbum = albumRepository.findById(albumId)
                        .orElseThrow(() -> new EntityNotFoundException("Album not found: " + albumId));
                existing.setBestAlbum(persistentAlbum);
            } else {
                Album newAlbum = musicBandMapper.toEntity(patch.getBestAlbum());
                newAlbum = albumRepository.save(newAlbum);
                existing.setBestAlbum(newAlbum);
            }
        }


        if (patch.getStudio() != null) {
            Long studioId = patch.getStudio().getId();

            if (studioId != null) {

                Studio persistentStudio = studioRepository.findById(studioId)
                        .orElseThrow(() -> new EntityNotFoundException("Studio not found: " + studioId));
                existing.setStudio(persistentStudio);
            } else {

                existing.setStudio(null);
            }
        }


        existing = musicBandRepository.save(existing);
        notifyClients("BAND_UPDATED");
        return musicBandMapper.toResponseDto(existing);
    }

    @Transactional
    public void delete(@NotNull Long id) {
        MusicBand musicBand = musicBandRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("MusicBand not found: " + id));
        musicBandRepository.delete(musicBand);

        notifyClients("BAND_UPDATED");
    }

    
    public boolean deleteOneByStudioName(String studioName) {
        Optional<MusicBand> musicBand = musicBandRepository.findFirstByStudioName(studioName);
        if (musicBand.isPresent()) {
            musicBandRepository.delete(musicBand.get());

            return true;
        }
        return false;
    }

    public Double getAverageAlbumCount() {
        return musicBandRepository.findAverageAlbumCount();
    }

    public long countByStudioNameGreaterThan(String studioName) {
        return musicBandRepository.countByStudioNameGreaterThan(studioName);
    }

    public List<MusicBandResponseDto> findByGenre(MusicGenre genre) {
        List<MusicBand> musicBands = musicBandRepository.findByGenre(genre);
        return musicBands.stream()
                .map(musicBandMapper::toResponseDto)
                .toList();
    }

    public long removeParticipant(Long id) {
        MusicBand musicBand = musicBandRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("MusicBand not found: " + id));

        if (musicBand.getNumberOfParticipants() > 1) {
            musicBand.setNumberOfParticipants(musicBand.getNumberOfParticipants() - 1);
            musicBandRepository.save(musicBand);

            notifyClients("BAND_UPDATED");
            return musicBand.getNumberOfParticipants();
        }
        return musicBand.getNumberOfParticipants();
    }

    
    private void validateDtoForImport(MusicBandCreateDto dto, int index) {
        String prefix = "Группа #" + (index + 1) + " (" + (dto.getName() != null ? dto.getName() : "N/A") + "): ";

        if (dto.getName() == null || dto.getName().trim().isEmpty()) {
            throw new ValidationException(prefix + "Поле 'name' не может быть null или пустым.");
        }
        if (dto.getCoordinates() == null) {
            throw new ValidationException(prefix + "Поле 'coordinates' не может быть null.");
        }
        if (dto.getGenre() == null) {
            throw new ValidationException(prefix + "Поле 'genre' не может быть null.");
        }
        if (dto.getNumberOfParticipants() <= 0) {
            throw new ValidationException(prefix + "Поле 'numberOfParticipants' должно быть > 0.");
        }
        if (dto.getSingleCount() == null || dto.getSingleCount() <= 0) {
            throw new ValidationException(prefix + "Поле 'singlesCount' не может быть null и должно быть > 0.");
        }
        if (dto.getDescription() == null) {
            throw new ValidationException(prefix + "Поле 'description' не может быть null.");
        }
        if (dto.getAlbumCount() <= 0) {
            throw new ValidationException(prefix + "Поле 'albumCount' должно быть > 0.");
        }
        if (dto.getEstablishmentDate() == null) {
            throw new ValidationException(prefix + "Поле 'establishmentDate' не может быть null.");
        }
    }



    @Transactional
    public ImportResultDto importBandsFromXml(InputStream xmlData, String originalFilename) {
        User currentUser = getCurrentAuthenticatedUser();
        ImportHistory history = null;
        String finalFileName = null;

        // --- ШАГ 1: Распарсить XML ---
        List<MusicBandCreateDto> dtos;
        byte[] xmlBytes; // Сохраняем байты для MinIO
        try {
            // Читаем весь InputStream в память
            xmlBytes = xmlData.readAllBytes();

            // Парсим XML из байтов
            JAXBContext jaxbContext = JAXBContext.newInstance(MusicBandListWrapper.class, MusicBandCreateDto.class);
            Unmarshaller unmarshaller = jaxbContext.createUnmarshaller();
            MusicBandListWrapper wrapper = (MusicBandListWrapper) unmarshaller.unmarshal(new ByteArrayInputStream(xmlBytes));
            dtos = wrapper.getMusicBands();

            // Валидация
            if (dtos == null) {
                throw new ValidationException("XML не содержит корневой элемент <musicBands>");
            }
            for (int i = 0; i < dtos.size(); i++) {
                validateDtoForImport(dtos.get(i), i);
                checkUniqueness(dtos.get(i), null);
            }
        } catch (Exception e) {
            handleImportError(null, null, "Ошибка парсинга XML: " + e.getMessage(), e);
            throw new RuntimeException("Ошибка парсинга XML", e);
        }

        // --- ШАГ 2: Подготовка ---
        String uniqueId = UUID.randomUUID().toString();
        finalFileName = "import-" + uniqueId + ".xml";

        history = new ImportHistory(currentUser);
        history.setFileName(finalFileName);
        history.setStatus(ImportStatus.PENDING);
        history.setStartTime(ZonedDateTime.now());
        history = importHistoryService.saveHistoryInNewTransaction(history);

        try {
            // --- ШАГ 3: Сохранить файл в MinIO ---
            try (InputStream inputForMinio = new ByteArrayInputStream(xmlBytes)) {
                minioClient.putObject(
                        PutObjectArgs.builder()
                                .bucket(minioBucketName)
                                .object(finalFileName)
                                .stream(inputForMinio, xmlBytes.length, -1)
                                .contentType("application/xml")
                                .build()
                );
            }

            // --- ШАГ 4: Импортировать данные в БД ---
            int importedCount = 0;
            for (MusicBandCreateDto dto : dtos) {
                this.create(dto);
                importedCount++;
            }
            notifyClients("BAND_BULK_IMPORTED");

            // --- ШАГ 5: Успех ---
            history.setFileName(finalFileName);
            history.setStatus(ImportStatus.SUCCESS);
            history.setAddedCount(importedCount);
            history.setEndTime(ZonedDateTime.now());
            importHistoryService.saveHistoryInNewTransaction(history);

            return new ImportResultDto(importedCount, "Импорт завершен успешно.", true);

        } catch (Exception e) {
            // --- ШАГ 6: Ошибка ---
            handleImportError(history, finalFileName, "Ошибка импорта: " + e.getMessage(), e);
            throw new RuntimeException("Импорт прерван из-за ошибки", e);
        }
    }

    // Новый метод для "переименования" файла в MinIO
    private void finalizeMinIOFile(String tempFileName, String finalFileName) throws Exception {
        // MinIO не поддерживает rename, поэтому копируем и удаляем
        minioClient.copyObject(
                CopyObjectArgs.builder()
                        .bucket(minioBucketName)
                        .object(finalFileName)
                        .source(CopySource.builder()
                                .bucket(minioBucketName)
                                .object(tempFileName)
                                .build())
                        .build()
        );
        minioClient.removeObject(
                RemoveObjectArgs.builder()
                        .bucket(minioBucketName)
                        .object(tempFileName)
                        .build()
        );
    }

    
    private User getCurrentAuthenticatedUser() {
        
        Object principal = SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        if (principal instanceof User) {
            return (User) principal;
        }

        
        
        return null;
    }


    // Обновленный метод обработки ошибок, включающий удаление файла из MinIO
    private void handleImportError(ImportHistory history, String fileName, String message, Exception e) {
        // Удаляем файл из MinIO, если имя известно
        if (fileName != null) {
            try {
                minioClient.removeObject(
                        RemoveObjectArgs.builder()
                                .bucket(minioBucketName)
                                .object(fileName)
                                .build()
                );
            } catch (Exception ex) {
                System.err.println("Не удалось удалить файл " + fileName + " из MinIO: " + ex.getMessage());
            }
        }

        // Обновляем статус в БД
        if (history != null) {
            history.setStatus(ImportStatus.FAILED);
            String errorMsg = e.getMessage() != null ? e.getMessage() : message;
            history.setErrorDetails(errorMsg.length() > 4096 ? errorMsg.substring(0, 4096) : errorMsg);
            history.setEndTime(ZonedDateTime.now());
            importHistoryService.saveHistoryInNewTransaction(history);
        }

        System.err.println(message + " Details: " + e.toString());
    }

    
    private void checkUniqueness(MusicBandCreateDto dto, Long bandIdToExclude) {
        if (dto.getName() == null || dto.getGenre() == null) {
            return;
        }

        List<MusicBand> bandsOfSameGenre = musicBandRepository.findByGenre(dto.getGenre());

        boolean exists = bandsOfSameGenre.stream()
                .filter(band -> bandIdToExclude == null || !band.getId().equals(bandIdToExclude))
                .anyMatch(band -> dto.getName().equalsIgnoreCase(band.getName()));

        if (exists) {
            throw new ValidationException(
                    "Нарушение уникальности: Группа с названием '" + dto.getName() +
                            "' и жанром '" + dto.getGenre().name() + "' уже существует."
            );
        }
    }
}