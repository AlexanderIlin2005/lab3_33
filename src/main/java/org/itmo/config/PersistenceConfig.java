package org.itmo.config;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import jakarta.persistence.EntityManagerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ResourceLoader;
import org.springframework.orm.jpa.JpaTransactionManager;
import org.springframework.orm.jpa.LocalContainerEntityManagerFactoryBean;
import org.springframework.orm.jpa.persistenceunit.DefaultPersistenceUnitManager;
import jakarta.persistence.spi.PersistenceUnitInfo;
import org.springframework.orm.jpa.vendor.HibernateJpaVendorAdapter;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.EnableTransactionManagement;

import javax.sql.DataSource;
import java.util.Properties;

@Configuration
@EnableTransactionManagement
public class PersistenceConfig {

    private final ResourceLoader resourceLoader;

    public PersistenceConfig(ResourceLoader resourceLoader) {
        this.resourceLoader = resourceLoader;
    }


    private Properties getJpaPropertiesFromXml() {
        DefaultPersistenceUnitManager persistenceUnitManager = new DefaultPersistenceUnitManager();
        persistenceUnitManager.setResourceLoader(this.resourceLoader);
        // Указываем, откуда брать persistence.xml
        persistenceUnitManager.setPersistenceXmlLocations("classpath:META-INF/persistence.xml");
        persistenceUnitManager.preparePersistenceUnitInfos();

        PersistenceUnitInfo info = persistenceUnitManager.obtainDefaultPersistenceUnitInfo();

        if (info != null) {
            // Возвращаем все свойства из XML
            return info.getProperties();
        }
        return new Properties();
    }


    @Bean(destroyMethod = "close")
    public HikariDataSource dataSource() {
        Properties jpaProperties = getJpaPropertiesFromXml();

        HikariConfig config = new HikariConfig();


        config.setJdbcUrl(jpaProperties.getProperty("jakarta.persistence.jdbc.url"));
        config.setUsername(jpaProperties.getProperty("jakarta.persistence.jdbc.user"));
        config.setPassword(jpaProperties.getProperty("jakarta.persistence.jdbc.password"));
        config.setDriverClassName(jpaProperties.getProperty("jakarta.persistence.jdbc.driver"));


        config.setMaximumPoolSize(20);
        config.setMinimumIdle(5);
        config.setConnectionTimeout(30000);
        config.setIdleTimeout(600000);
        config.setMaxLifetime(1800000);
        config.setConnectionTestQuery("SELECT 1");

        return new HikariDataSource(config);
    }

    @Bean
    public LocalContainerEntityManagerFactoryBean entityManagerFactory(DataSource dataSource) {
        LocalContainerEntityManagerFactoryBean em = new LocalContainerEntityManagerFactoryBean();
        em.setDataSource(dataSource);
        em.setPersistenceUnitName("my-persistence-unit");


        HibernateJpaVendorAdapter vendorAdapter = new HibernateJpaVendorAdapter();

        vendorAdapter.setDatabasePlatform("org.hibernate.dialect.PostgreSQLDialect");
        vendorAdapter.setGenerateDdl(false);
        em.setJpaVendorAdapter(vendorAdapter);

        Properties finalProperties = new Properties();
        Properties xmlProperties = getJpaPropertiesFromXml();


        finalProperties.setProperty("jakarta.persistence.jdbc.url", xmlProperties.getProperty("jakarta.persistence.jdbc.url"));
        finalProperties.setProperty("jakarta.persistence.jdbc.user", xmlProperties.getProperty("jakarta.persistence.jdbc.user"));
        finalProperties.setProperty("jakarta.persistence.jdbc.password", xmlProperties.getProperty("jakarta.persistence.jdbc.password"));
        finalProperties.setProperty("jakarta.persistence.jdbc.driver", xmlProperties.getProperty("jakarta.persistence.jdbc.driver"));



        finalProperties.setProperty("hibernate.dialect", "org.hibernate.dialect.PostgreSQLDialect");
        finalProperties.setProperty("hibernate.hbm2ddl.auto", "none");
        finalProperties.setProperty("hibernate.show_sql", "true");
        finalProperties.setProperty("hibernate.format_sql", "true");



        em.setJpaProperties(finalProperties);

        return em;
    }


    @Bean
    public PlatformTransactionManager transactionManager(EntityManagerFactory entityManagerFactory) {
        JpaTransactionManager transactionManager = new JpaTransactionManager();
        transactionManager.setEntityManagerFactory(entityManagerFactory);
        return transactionManager;
    }
}