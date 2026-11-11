package com.libreria.servidor.config;

import com.mongodb.ConnectionString;
import com.mongodb.MongoClientSettings;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.mongodb.config.AbstractMongoClientConfiguration;
import org.springframework.data.mongodb.repository.config.EnableMongoRepositories;

import java.util.Collection;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Conexión con MongoDB (como el MongoConfig del POC del profesor).
 * La URL y la base se leen de application.properties (libreria.mongo.uri y libreria.mongo.base);
 * las pruebas usan la base libreria_test.
 */
@Configuration
@EnableMongoRepositories(basePackages = "com.libreria.servidor.repository")
public class MongoConfig extends AbstractMongoClientConfiguration {

    @Value("${libreria.mongo.uri:mongodb://localhost:27017}")
    private String uri;

    @Value("${libreria.mongo.base:libreria_evaluacion}")
    private String base;

    @Override
    protected String getDatabaseName() {
        return base;
    }

    @Override
    protected void configureClientSettings(MongoClientSettings.Builder builder) {
        builder.applyConnectionString(new ConnectionString(uri))
                .applyToClusterSettings(cluster -> cluster.serverSelectionTimeout(5, TimeUnit.SECONDS));
    }

    /** Crea los índices declarados con @Indexed y @CompoundIndex al arrancar. */
    @Override
    protected boolean autoIndexCreation() {
        return true;
    }

    @Override
    protected Collection<String> getMappingBasePackages() {
        return List.of("com.libreria.servidor.model");
    }
}
