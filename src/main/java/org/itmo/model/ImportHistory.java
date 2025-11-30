package org.itmo.model;

import org.itmo.model.enums.ImportStatus;


import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import lombok.NoArgsConstructor;

import jakarta.persistence.Cacheable;

import java.time.ZonedDateTime;

@Entity
@Cacheable
@Table(name = "import_history")
@Getter
@Setter
@NoArgsConstructor
public class ImportHistory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "launched_by_id", nullable = false)
    private User launchedBy;

    @Column(name = "start_time", nullable = false)
    private ZonedDateTime startTime = ZonedDateTime.now();

    @Column(name = "end_time")
    private ZonedDateTime endTime;

    
    
    
    @Enumerated(EnumType.STRING)
    @Column(
            nullable = false,
            
            columnDefinition = "VARCHAR(50)"
    )
    private ImportStatus status = ImportStatus.PENDING;

    @Column(name = "added_count")
    private Integer addedCount;

    @Column(name = "error_details", columnDefinition = "TEXT")
    private String errorDetails;

    // Новое поле для имени файла в MinIO ...
    @Column(name = "file_name")
    private String fileName;

    public ImportHistory(User launchedBy) {
        this.launchedBy = launchedBy;
    }
}