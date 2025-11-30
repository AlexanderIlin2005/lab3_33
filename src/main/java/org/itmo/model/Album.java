package org.itmo.model;

import jakarta.persistence.*;
import jakarta.validation.constraints.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import jakarta.persistence.Cacheable;

@Data
@NoArgsConstructor
@Entity
@Cacheable
@Table(name = "album")
public class Album {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotBlank
    @Column(nullable = false)
    private String name;

    @Min(1)
    private int tracks;

    @Min(1)
    private int length;

    @NotNull
    @Min(1)
    private Double sales;
}
