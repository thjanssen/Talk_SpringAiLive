package com.thorben.janssen.piratestore.product;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface ProductRepository extends JpaRepository<Product, Long> {

    List<Product> findByActiveTrueOrderByNameAsc();

    List<Product> findByActiveTrueAndCategoryOrderByNameAsc(ProductCategory category);

    Optional<Product> findBySku(String sku);

    /** Case-insensitive search across name, description and category, used by the support assistant tool. */
    @Query("""
            select p from Product p
            where p.active = true
              and (lower(p.name) like lower(concat('%', :term, '%'))
                or lower(p.description) like lower(concat('%', :term, '%'))
                or lower(cast(p.category as string)) like lower(concat('%', :term, '%')))
            order by p.name asc
            """)
    List<Product> search(@Param("term") String term);
}
