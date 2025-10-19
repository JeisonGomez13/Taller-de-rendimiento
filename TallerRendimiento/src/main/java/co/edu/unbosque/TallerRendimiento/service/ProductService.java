package co.edu.unbosque.TallerRendimiento.service;



import java.math.BigDecimal;

import java.util.List;

import java.util.Optional;



import org.springframework.cache.Cache; // Nuevo Importe

import org.springframework.beans.factory.annotation.Autowired;

import org.springframework.cache.CacheManager;

import org.springframework.cache.annotation.CacheEvict;

import org.springframework.cache.annotation.Cacheable;

import org.springframework.stereotype.Service;

import org.springframework.transaction.annotation.Transactional;



import co.edu.unbosque.TallerRendimiento.model.Producto;

import co.edu.unbosque.TallerRendimiento.model.TransInventario;

import co.edu.unbosque.TallerRendimiento.model.Usuario;

import co.edu.unbosque.TallerRendimiento.repository.ProductoRepository;

import co.edu.unbosque.TallerRendimiento.repository.TransInventarioRepository;

import co.edu.unbosque.TallerRendimiento.repository.UsuarioRepository;



@Service

public class ProductService {



    @Autowired

    private ProductoRepository productoRepository;



    @Autowired

    private TransInventarioRepository transInventarioRepository;



    @Autowired

    private UsuarioRepository usuarioRepository;



    // ⬅️ Inyección de Cache Managers para la estrategia L1/L2

    @Autowired

    private CacheManager redisCacheManager; // L2 (Distribuido)



    @Autowired

    private CacheManager caffeineCacheManager; // L1 (En memoria)



    /**

     * Busca productos basándose en filtros. Usa @Cacheable, recurriendo al manager @Primary (Redis).

     */

    @Cacheable("productCache")

    public List<Producto> buscarProductos(String query, String category, BigDecimal minPrice) {

        return productoRepository.searchProducts(query, category, minPrice);

    }



    /**

     * Obtiene los detalles de un producto por su ID usando la estrategia Cache Multi-Nivel.

     * Flujo de lectura: L1 (Caffeine) -> L2 (Redis) -> Base de Datos.

     */

    // 🚨 Eliminamos @Cacheable aquí para implementar la lógica de cascada manualmente

    // Contenido del método obtenerDetallesProducto en ProductService.java

public Optional<Producto> obtenerDetallesProducto(Integer id) {
    Cache caffeineCache = caffeineCacheManager.getCache("productCache");
    Cache redisCache = redisCacheManager.getCache("productCache");

    // 1. INTENTAR L1 (Caffeine): Lectura ultra-rápida en memoria
    Producto productoL1 = caffeineCache.get(id, Producto.class);
    if (productoL1 != null) {
        System.out.println(">>> Cache Hit: L1 (Caffeine) - Latencia más baja.");
        return Optional.of(productoL1);
    }

    // 2. INTENTAR L2 (Redis): Lectura distribuida
    Producto productoL2 = redisCache.get(id, Producto.class);
    if (productoL2 != null) {
        System.out.println(">>> Cache Hit: L2 (Redis) - Acceso compartido.");
        // Si hay un hit en L2, se hace Write-Through a L1 para la siguiente petición.
        caffeineCache.put(id, productoL2);
        return Optional.of(productoL2);
    }

    // 3. CACHE MISS: Ir a la Base de Datos
    System.out.println(">>> Cache Miss: Acceso a la Base de Datos (Lento).");
    Optional<Producto> productOpt = productoRepository.findById(id);

    // 4. ESCRITURA (Write-Through Modificada): 
    // Si se encuentra, AHORA SOLO ESCRIBE EN L1. 
    // L2 (Redis) solo se actualizará cuando L1 expire y el dato deba ser traído desde L2 (que en este caso es un Miss).
    productOpt.ifPresent(p -> {
        // 🚨 CAMBIO CRÍTICO: SOLO ESCRIBIMOS EN L1. SE ELIMINA LA ESCRITURA A REDIS AQUÍ.
        // redisCache.put(id, p); // ❌ ¡LÍNEA ELIMINADA!
        caffeineCache.put(id, p); // Escribir solo en L1
    });

    return productOpt;
}



    /**

     * Actualiza el stock de un producto y registra la transacción de inventario.

     * Política: Write-Behind (Actualiza la DB) + Invalidation (Elimina el caché).

     */

    @Transactional

    // ➡️ Invalida la entrada específica en L2 (Redis). L1 se sincronizará en la próxima lectura.

    @CacheEvict(value = "productCache", key = "#productId")

    public Optional<Producto> actualizarStock(Integer productId, Integer quantityChange, String description, Integer userId) {

        Optional<Producto> productOpt = productoRepository.findById(productId);

        Optional<Usuario> userOpt = usuarioRepository.findById(userId);



        if (productOpt.isEmpty() || userOpt.isEmpty()) {

            return Optional.empty();

        }



        Producto product = productOpt.get();

        Usuario user = userOpt.get();



        // 1. Actualizar la cantidad del producto (DB Write - Bloqueante)

        product.setCantidadProducto(product.getCantidadProducto() + quantityChange);

        Producto updatedProduct = productoRepository.save(product);



        // 2. Registrar la transacción de inventario (DB Write - Bloqueante)

        String tipo = quantityChange > 0 ? "Entrada" : "Ajuste";

        TransInventario trans = new TransInventario(

            tipo,

            java.time.LocalDate.now(),

            Math.abs(quantityChange),

            description,

            product,

            user

        );

        transInventarioRepository.save(trans);



        // NOTA: El @CacheEvict arriba garantiza que el dato desactualizado se elimina de L2.

        // Si necesitas consistencia instantánea en L1, también podrías añadir:

        // caffeineCacheManager.getCache("productCache").evict(productId);

        // Aunque generalmente @CacheEvict en L2 es suficiente.

       

        return Optional.of(updatedProduct);

    }



    /**

     * Obtiene los productos con bajo stock. Usa @Cacheable, recurriendo al manager @Primary (Redis).

     */

    @Cacheable("productCache")

    public List<Producto> obtenerProductosBajoStock(Integer threshold) {

        return productoRepository.findByCantidadProductoLessThan(threshold);

    }

}