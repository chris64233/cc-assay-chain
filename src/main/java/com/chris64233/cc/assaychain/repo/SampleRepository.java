package com.chris64233.cc.assaychain.repo;

import com.chris64233.cc.assaychain.domain.Sample;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface SampleRepository extends JpaRepository<Sample, Long> {

    Optional<Sample> findByCode(String code);

    Optional<Sample> findByExternalSampleNo(String externalSampleNo);

    /** 行级悲观锁，所有对样本状态/保管方的变更都在锁内完成，杜绝并发双重操作。 */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from Sample s where s.id = :id")
    Optional<Sample> lockById(@Param("id") Long id);

    /** 同一父样本下的子样本，用于分样幂等重放时比对内容。 */
    List<Sample> findByParentIdOrderByIdAsc(Long parentId);

    /** 沿物化路径向下取整棵子树。 */
    @Query("select s from Sample s where s.materialPath like :pathPrefix order by s.depth asc, s.id asc")
    List<Sample> findDescendants(@Param("pathPrefix") String pathPrefix, Pageable pageable);

    @Query("select s from Sample s where s.materialPath like :pathPrefix order by s.depth asc, s.id asc")
    List<Sample> findAllInPath(@Param("pathPrefix") String pathPrefix);
}
