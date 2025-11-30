package org.itmo.config;

import jakarta.persistence.EntityManagerFactory;
import org.springframework.context.annotation.*;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.orm.jpa.JpaTransactionManager;
import org.springframework.orm.jpa.LocalContainerEntityManagerFactoryBean;
import org.springframework.orm.jpa.vendor.EclipseLinkJpaVendorAdapter;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.EnableTransactionManagement;

import io.minio.MinioClient;
import org.springframework.beans.factory.annotation.Value;

@Configuration
@ComponentScan("org.itmo")
@EnableJpaRepositories(basePackages = "org.itmo.repository")
@EnableTransactionManagement
@EnableAspectJAutoProxy
@Import({SecurityConfig.class, PasswordEncoderConfig.class, JacksonConfig.class})
public class AppConfig {

    @Bean
    public LocalContainerEntityManagerFactoryBean entityManagerFactory() {
        LocalContainerEntityManagerFactoryBean em = new LocalContainerEntityManagerFactoryBean();
        em.setPersistenceUnitName("my-persistence-unit");
        em.setJpaVendorAdapter(new EclipseLinkJpaVendorAdapter());

        
        
        
        em.setPackagesToScan("org.itmo");

        return em;
    }

    @Bean
    public PlatformTransactionManager transactionManager(EntityManagerFactory emf) {
        JpaTransactionManager transactionManager = new JpaTransactionManager();
        transactionManager.setEntityManagerFactory(emf);
        return transactionManager;
    }



    @Value("${minio.url:http://localhost:9000}")
    private String minioUrl;

    @Value("${minio.access.key:minioadmin}")
    private String minioAccessKey;

    @Value("${minio.secret.key:minioadmin}")
    private String minioSecretKey;

    @Value("${minio.bucket.name:import-files}")
    private String minioBucketName;

    @Bean
    public MinioClient minioClient() {
        return MinioClient.builder()
                .endpoint(minioUrl)
                .credentials(minioAccessKey, minioSecretKey)
                .build();
    }

    @Bean
    public String minioBucketName() {
        return minioBucketName;
    }
}