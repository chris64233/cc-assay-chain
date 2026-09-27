package com.chris64233.cc.assaychain.repo;

import com.chris64233.cc.assaychain.domain.CorrectionRequest;
import com.chris64233.cc.assaychain.domain.CorrectionStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface CorrectionRequestRepository extends JpaRepository<CorrectionRequest, Long> {

    Optional<CorrectionRequest> findByCorrectionNo(String correctionNo);

    boolean existsByReservedEventNo(String reservedEventNo);

    /** 标量投影取更正申请主键，不把实体载入持久化上下文。 */
    @Query("select c.id from CorrectionRequest c where c.correctionNo = :correctionNo")
    Optional<Long> findIdByCorrectionNo(@Param("correctionNo") String correctionNo);

    /** 标量投影：更正申请所引用原版本 id（避免懒加载把原版本实体提前载入上下文）。 */
    @Query("select c.originalVersion.id from CorrectionRequest c where c.id = :id")
    Long findOriginalVersionIdById(@Param("id") Long id);

    /** 标量投影：更正申请所引用原版本所属样本 id（保证「样本优先」的锁顺序）。 */
    @Query("select c.originalVersion.sample.id from CorrectionRequest c where c.id = :id")
    Long findSampleIdById(@Param("id") Long id);

    boolean existsByOriginalVersionIdAndStatus(Long originalVersionId, CorrectionStatus status);

    /** 行级悲观锁加载更正申请，用于审批并发临界区。 */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from CorrectionRequest c where c.id = :id")
    Optional<CorrectionRequest> findByIdForUpdate(@Param("id") Long id);

    @Query("select c from CorrectionRequest c "
            + "where c.originalVersion.sample.id in :sampleIds order by c.requestedAt asc")
    List<CorrectionRequest> findBySampleIdInOrderByRequestedAtAsc(@Param("sampleIds") List<Long> sampleIds);
}
