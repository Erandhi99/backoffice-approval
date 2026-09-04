package com.senfin.backoffice_approval.entity;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "funds", uniqueConstraints = {
        @UniqueConstraint(name = "uk_funds_slug", columnNames = "slug")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Fund {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 150)
    private String name;

    @Column(nullable = false, length = 80)
    private String slug;

    @Column(nullable = false, length = 500)
    private String url;
}
