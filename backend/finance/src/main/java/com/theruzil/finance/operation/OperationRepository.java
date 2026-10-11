package com.theruzil.finance.operation;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.theruzil.finance.category.CategoryType;

public interface OperationRepository extends JpaRepository<Operation, Long> {

    Optional<Operation> findByCategoryIdAndOperationDate(Long categoryId, LocalDate operationDate);

    List<Operation> findByUserIdAndOperationDateOrderById(Long userId, LocalDate operationDate);

    List<Operation> findByUserIdAndOperationDateBetweenOrderById(Long userId, LocalDate from, LocalDate to);

    Optional<Operation> findByIdAndUserId(Long id, Long userId);

    boolean existsByCategoryId(Long categoryId);

    @Query("select coalesce(sum(o.amount), 0) from Operation o join Category c on o.categoryId = c.id "
            + "where o.userId = :userId and c.type = :type")
    BigDecimal sumAmountByUserIdAndCategoryType(@Param("userId") Long userId, @Param("type") CategoryType type);
}
