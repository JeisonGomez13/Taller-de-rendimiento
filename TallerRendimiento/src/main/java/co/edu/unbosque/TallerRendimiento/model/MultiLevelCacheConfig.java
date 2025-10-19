package co.edu.unbosque.TallerRendimiento.model;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.jsontype.impl.LaissezFaireSubTypeValidator;
import com.github.benmanes.caffeine.cache.Caffeine; 

import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.caffeine.CaffeineCacheManager; // ⬅️ Nuevo Importe: EL QUE GARANTIZA LA EXPIRACIÓN
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary; 

import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.serializer.RedisSerializationContext.SerializationPair;
import org.springframework.data.redis.serializer.Jackson2JsonRedisSerializer; 
import org.springframework.data.redis.serializer.RedisSerializer;

import java.time.Duration;
import java.util.concurrent.TimeUnit; 

@Configuration
@EnableCaching
public class MultiLevelCacheConfig {

    // --- L2: CONFIGURACIÓN BASE DE REDIS (Serialización y TTL) ---
    @Bean
    public RedisCacheConfiguration redisCacheConfiguration() {
        ObjectMapper objectMapper = new ObjectMapper();
        
        objectMapper.activateDefaultTyping(
            LaissezFaireSubTypeValidator.instance, 
            ObjectMapper.DefaultTyping.NON_FINAL
        );
        
        RedisSerializer<Object> jacksonSerializer = new Jackson2JsonRedisSerializer<>(objectMapper, Object.class);

        return RedisCacheConfiguration.defaultCacheConfig()
            .disableCachingNullValues()
            // TTL de L2 (Redis) - Puedes dejarlo más largo, por ejemplo, 10 minutos.
            .entryTtl(Duration.ofMinutes(10)) 
            .serializeValuesWith(SerializationPair.fromSerializer(jacksonSerializer));
    }
    
    // --- L1: CAFFEINE CACHE MANAGER (El Fix) ---
    @Bean("caffeineCacheManager")
    public CacheManager caffeineCacheManager() {
        // 🚨 USAMOS CaffeineCacheManager DE SPRING PARA GARANTIZAR QUE LA EXPIRACIÓN FUNCIONE
        CaffeineCacheManager cacheManager = new CaffeineCacheManager();
        
        // Creamos la configuración de Caffeine (TTL de 30 segundos)
        Caffeine<Object, Object> caffeineBuilder = Caffeine.newBuilder()
            .expireAfterWrite(10, TimeUnit.SECONDS) // ⬅️ ¡ESTO AHORA FUNCIONARÁ FIABLEMENTE!
            .maximumSize(500); 

        // Aplicamos la configuración a los caches que usaremos (en este caso, 'productCache')
        cacheManager.setCaffeine(caffeineBuilder);
        
        // Necesitamos decirle explícitamente qué caches debe gestionar, o Spring lo hará automáticamente.
        // Lo nombraremos explícitamente para mayor seguridad:
        cacheManager.setCacheNames(java.util.Collections.singletonList("productCache")); 

        return cacheManager;
    }

    // --- L2: REDIS CACHE MANAGER ---
    @Bean("redisCacheManager")
    @Primary 
    public RedisCacheManager redisCacheManager(RedisConnectionFactory connectionFactory, RedisCacheConfiguration redisCacheConfiguration) {
        return RedisCacheManager.builder(connectionFactory)
                .cacheDefaults(redisCacheConfiguration)
                .transactionAware()
                .build();
    }
}