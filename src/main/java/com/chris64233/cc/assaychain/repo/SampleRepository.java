package com.chris64233.cc.assaychain.repo;

import com.chris64233.cc.assaychain.domain.Sample;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

public interface SampleRepository extends JpaRepository<Sample, Long> {

    Optional<Sample> findByExternalNo(String externalNo);

    List<Sample> findByParentId(Long parentId);

    List<Sample> findByParentIdIn(List<Long> parentIds);

    boolean existsByExternalNo(String externalNo);

    /** 行级悲观写锁：串行化同一样本上的发布结果/分样/交接/审批。 */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from Sample s where s.externalNo = :externalNo")
    Optional<Sample> findLockedByExternalNo(String externalNo);

    /** 行级悲观写锁（按主键）。 */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from Sample s where s.id = :id")
    Optional<Sample> findLockedById(Long id);
}
