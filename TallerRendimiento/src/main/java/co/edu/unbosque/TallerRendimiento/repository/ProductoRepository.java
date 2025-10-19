package co.edu.unbosque.TallerRendimiento.repository;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import co.edu.unbosque.TallerRendimiento.model.Producto;

@Repository
public interface ProductoRepository extends JpaRepository<Producto, Integer> {

    // GET /api/products/search: Búsqueda sin optimización (LIKE '%producto%') 

    @Query("SELECT p FROM Producto p " +
        "JOIN p.subcategoria s " +
        "JOIN s.categoria c " +
        // Filtro de búsqueda (LIKE)
        // Si :query es NULL, COALESCE lo reemplaza con p.nombreProducto, y la búsqueda es TRUE.
        "WHERE (LOWER(p.nombreProducto) LIKE LOWER(CONCAT('%', COALESCE(:query, p.nombreProducto), '%')) OR :query IS NULL) " +
        
        // Filtro de categoría (=)
        // Si :category es NULL, COALESCE lo reemplaza con c.nombreCategoria, y la comparación es TRUE.
        "AND (LOWER(c.nombreCategoria) = LOWER(COALESCE(:category, c.nombreCategoria)) OR :category IS NULL) " +
        
        // Filtro de precio (>=)
        "AND (:minPrice IS NULL OR p.precioProducto >= :minPrice)")
    List<Producto> searchProducts(
        @Param("query") String query,
        @Param("category") String category,
        @Param("minPrice") BigDecimal minPrice);

    //  @Query("SELECT p FROM Producto p " +
    //     "JOIN p.subcategoria s " +
    //     "JOIN s.categoria c " +
    //     // CORRECCIÓN CON COALESCE: Asegura que LOWER() no reciba un NULL sin tipo.
    //     // Si :query es NULL, se compara con sí mismo (lo cual será TRUE si se pasa la primera condición)
    //     "WHERE (LOWER(p.nombreProducto) LIKE LOWER(CONCAT('%', COALESCE(:query, p.nombreProducto), '%')) OR :query IS NULL) " +
    //     "AND (LOWER(c.nombreCategoria) = LOWER(COALESCE(:category, c.nombreCategoria)) OR :category IS NULL) " +
    //     "AND (:minPrice IS NULL OR p.precioProducto >= :minPrice)")
    // Page<Producto> searchProducts(
    //     @Param("query") String query,
    //     @Param("category") String category,
    //     @Param("minPrice") BigDecimal minPrice,
    //     Pageable pageable);

    // GET /api/inventory/low-stock
    List<Producto> findByCantidadProductoLessThan(Integer threshold);

    // GET /api/reports/top-selling: [cite: 72]
    
    @Query(value = "SELECT p.* FROM producto p " +
                "JOIN trans_inventario ti ON p.id_producto = ti.id_producto " +
                "WHERE ti.tipo_trans_inventario = 'Venta' " +
                "AND ti.fecha_trans_inventario::DATE >= :startDate " +  // <- Caster el TIMESTAMP a DATE
                "GROUP BY p.id_producto " +
                "ORDER BY SUM(ti.cantidad_trans_inventario) DESC " +
                "LIMIT 10", nativeQuery = true)
    List<Producto> findTopSellingProducts(@Param("startDate") LocalDate startDate);
}