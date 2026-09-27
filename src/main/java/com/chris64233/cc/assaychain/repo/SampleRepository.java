package com.chris64233.cc.assaychain.repo;

import com.chris64233.cc.assaychain.domain.Sample;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface SampleRepository extends JpaRepository<Sample, Long> {

    Optional<Sample> findByExternalNo(String externalNo);

    /** 行级悲观锁按外部号加载样本，用于结果提交/复核/更正与分样/交接的并发临界区。 */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from Sample s where s.externalNo = :externalNo")
    Optional<Sample> findByExternalNoForUpdate(@Param("externalNo") String externalNo);

    /** 行级悲观锁按主键加载样本。 */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from Sample s where s.id = :id")
    Optional<Sample> findByIdForUpdate(@Param("id") Long id);

    List<Sample> findByParentId(Long parentId);

    List<Sample> findByParentIdIn(List<Long> parentIds);

    boolean existsByExternalNo(String externalNo);
}
