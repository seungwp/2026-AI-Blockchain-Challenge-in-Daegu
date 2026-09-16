package com.golmok.oneweek.repository;

import com.golmok.oneweek.entity.IngredientPrice;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface IngredientPriceRepository extends JpaRepository<IngredientPrice, Long> {
    List<IngredientPrice> findByItemIn(List<String> items);
}
