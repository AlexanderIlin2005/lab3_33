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

    /**
     * Извлекает свойства JDBC из persistence.xml
     */
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

    /**
     * Конфигурация HikariCP с использованием свойств из XML.
     * Мы используем стандартный DataSource, который Hibernate будет использовать.
     */
    @Bean(destroyMethod = "close")
    public HikariDataSource dataSource() {
        Properties jpaProperties = getJpaPropertiesFromXml();

        HikariConfig config = new HikariConfig();

        // Свойства подключения из persistence.xml
        config.setJdbcUrl(jpaProperties.getProperty("jakarta.persistence.jdbc.url"));
        config.setUsername(jpaProperties.getProperty("jakarta.persistence.jdbc.user"));
        config.setPassword(jpaProperties.getProperty("jakarta.persistence.jdbc.password"));
        config.setDriverClassName(jpaProperties.getProperty("jakarta.persistence.jdbc.driver"));

        // Параметры пула HikariCP
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
        em.setDataSource(dataSource); //  Передаем DataSource (HikariCP)
        em.setPersistenceUnitName("my-persistence-unit");

        // 1. Используем адаптер Hibernate
        HibernateJpaVendorAdapter vendorAdapter = new HibernateJpaVendorAdapter();
        // Указываем PostgreSQL Dialect (для Hibernate)
        vendorAdapter.setDatabasePlatform("org.hibernate.dialect.PostgreSQLDialect");
        vendorAdapter.setGenerateDdl(false);
        em.setJpaVendorAdapter(vendorAdapter);

        Properties finalProperties = new Properties();
        Properties xmlProperties = getJpaPropertiesFromXml();

        // 2. Учетные данные JDBC больше НЕ НУЖНЫ, т.к. они передаются через setDataSource(dataSource)!
        // Однако, для корректной работы Hibernate-провайдера, который может их запросить,
        // мы все равно их оставляем, но уже без риска конфликта с пулом.
        finalProperties.setProperty("jakarta.persistence.jdbc.url", xmlProperties.getProperty("jakarta.persistence.jdbc.url"));
        finalProperties.setProperty("jakarta.persistence.jdbc.user", xmlProperties.getProperty("jakarta.persistence.jdbc.user"));
        finalProperties.setProperty("jakarta.persistence.jdbc.password", xmlProperties.getProperty("jakarta.persistence.jdbc.password"));
        finalProperties.setProperty("jakarta.persistence.jdbc.driver", xmlProperties.getProperty("jakarta.persistence.jdbc.driver"));


        // 3. Свойства Hibernate
        // Указываем диалект (повторяем то же, что и в vendorAdapter)
        finalProperties.setProperty("hibernate.dialect", "org.hibernate.dialect.PostgreSQLDialect");
        // Включаем или выключаем автоматическую генерацию схемы
        finalProperties.setProperty("hibernate.hbm2ddl.auto", "none");
        // Дополнительные свойства для логов
        finalProperties.setProperty("hibernate.show_sql", "true");
        finalProperties.setProperty("hibernate.format_sql", "true");

        // 4. Настройка кэша (если нужен JCache, можно оставить, но для простоты уберем EclipseLink-специфичные)
        // Если вы используете JCache/Ehcache, вам нужно убедиться, что его интеграция настроена правильно для Hibernate.
        // Для начала я удалю специфичные свойства EclipseLink/JCache.

        // ВАЖНО: Hibernate по умолчанию использует другой механизм кэша L2.
        // Если вам нужен Ehcache, потребуется дополнительная настройка (зависимости и свойства).
        // Если Ehcache настроен для Hibernate, можно добавить:
        // finalProperties.setProperty("hibernate.cache.region.factory_class", "org.hibernate.cache.jcache.JCacheRegionFactory");
        // finalProperties.setProperty("jakarta.persistence.sharedCache.mode", "ENABLE_SELECTIVE");


        em.setJpaProperties(finalProperties);

        // Важно: LocalContainerEntityManagerFactoryBean автоматически найдет пакеты с @Entity,
        // но можно явно указать их: em.setPackagesToScan("org.itmo.model");
        // В нашем случае, поскольку мы используем persistence.xml, он должен найти классы, указанные там.

        return em;
    }

    /**
     * Настройка Транзакционного Менеджера (остается без изменений)
     */
    @Bean
    public PlatformTransactionManager transactionManager(EntityManagerFactory entityManagerFactory) {
        JpaTransactionManager transactionManager = new JpaTransactionManager();
        transactionManager.setEntityManagerFactory(entityManagerFactory);
        return transactionManager;
    }
}