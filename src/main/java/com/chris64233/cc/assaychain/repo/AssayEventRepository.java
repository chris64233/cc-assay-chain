package com.chris64233.cc.assaychain.repo;

import com.chris64233.cc.assaychain.domain.AssayEvent;
import com.chris64233.cc.assaychain.domain.ResultStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface AssayEventRepository extends JpaRepository<AssayEvent, Long> {

    Optional<AssayEvent> findByEventNo(String eventNo);

    boolean existsByEventNo(String eventNo);

    /** 只取结果号对应的主键（标量投影，不把实体载入持久化上下文，避免后续加锁命中一级缓存）。 */
    @Query("select a.id from AssayEvent a where a.eventNo = :eventNo")
    Optional<Long> findIdByEventNo(@Param("eventNo") String eventNo);

    /** 行级悲观锁加载某个结果版本，用于审批与更正的并发临界区。 */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select a from AssayEvent a where a.id = :id")
    Optional<AssayEvent> findByIdForUpdate(@Param("id") Long id);

    /** （样本，项目）的最大版本号。 */
    Optional<AssayEvent> findTopBySampleIdAndItemCodeOrderByVersionNoDesc(
            Long sampleId, String itemCode);

    /** 按版本号升序返回（样本，项目）的完整版本链。 */
    List<AssayEvent> findBySampleIdAndItemCodeOrderByVersionNoAsc(
            Long sampleId, String itemCode);

    Optional<AssayEvent> findBySampleIdAndItemCodeAndStatus(
            Long sampleId, String itemCode, ResultStatus status);

    /** 取结果版本所属样本 id（不初始化实体，便于先锁样本行）。 */
    @Query("select a.sample.id from AssayEvent a where a.id = :id")
    Long findSampleIdById(@Param("id") Long id);

    List<AssayEvent> findBySampleIdInOrderByEventTimeAsc(List<Long> sampleIds);
}
