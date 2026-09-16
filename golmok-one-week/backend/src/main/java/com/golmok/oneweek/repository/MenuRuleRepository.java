package com.golmok.oneweek.repository;

import com.golmok.oneweek.entity.Enums.MenuCategory;
import com.golmok.oneweek.entity.MenuRule;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Collection;
import java.util.List;

public interface MenuRuleRepository extends JpaRepository<MenuRule, Long> {
    List<MenuRule> findByMenuCategoryIn(Collection<MenuCategory> categories);
}
