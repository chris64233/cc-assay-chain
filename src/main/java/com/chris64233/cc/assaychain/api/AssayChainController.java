package com.chris64233.cc.assaychain.api;

import com.chris64233.cc.assaychain.api.dto.AssaySubmitRequest;
import com.chris64233.cc.assaychain.api.dto.CorrectionDecisionRequest;
import com.chris64233.cc.assaychain.api.dto.CorrectionRequest;
import com.chris64233.cc.assaychain.api.dto.CustodyConfirmRequest;
import com.chris64233.cc.assaychain.api.dto.CustodyInitiateRequest;
import com.chris64233.cc.assaychain.api.dto.ReceiveRequest;
import com.chris64233.cc.assaychain.api.dto.ReviewRequest;
import com.chris64233.cc.assaychain.api.dto.SplitRequest;
import com.chris64233.cc.assaychain.domain.ApprovalEvent;
import com.chris64233.cc.assaychain.domain.AssayCorrection;
import com.chris64233.cc.assaychain.domain.AssayEvent;
import com.chris64233.cc.assaychain.domain.CustodyEvent;
import com.chris64233.cc.assaychain.domain.Sample;
import com.chris64233.cc.assaychain.domain.SplitEvent;
import com.chris64233.cc.assaychain.service.AssayService;
import com.chris64233.cc.assaychain.service.CurrentResultView;
import com.chris64233.cc.assaychain.service.CustodyService;
import com.chris64233.cc.assaychain.service.LineageService;
import com.chris64233.cc.assaychain.service.LineageView;
import com.chris64233.cc.assaychain.service.ReceptionService;
import com.chris64233.cc.assaychain.service.ResultHistoryView;
import com.chris64233.cc.assaychain.service.ResultQueryService;
import com.chris64233.cc.assaychain.service.ReviewService;
import com.chris64233.cc.assaychain.service.SampleResultsLineageView;
import com.chris64233.cc.assaychain.service.SampleView;
import com.chris64233.cc.assaychain.service.SplitService;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;

@RestController
@RequestMapping("/api")
public class AssayChainController {

    private final ReceptionService receptionService;
    private final SplitService splitService;
    private final CustodyService custodyService;
    private final AssayService assayService;
    private final ReviewService reviewService;
    private final ResultQueryService resultQueryService;
    private final LineageService lineageService;

    public AssayChainController(ReceptionService receptionService,
                                SplitService splitService,
                                CustodyService custodyService,
                                AssayService assayService,
                                ReviewService reviewService,
                                ResultQueryService resultQueryService,
                                LineageService lineageService) {
        this.receptionService = receptionService;
        this.splitService = splitService;
        this.custodyService = custodyService;
        this.assayService = assayService;
        this.reviewService = reviewService;
        this.resultQueryService = resultQueryService;
        this.lineageService = lineageService;
    }

    /** 接收原始矿样。 */
    @PostMapping("/samples")
    @ResponseStatus(HttpStatus.CREATED)
    public SampleView receive(@RequestBody ReceiveRequest request) {
        Sample sample = receptionService.receive(
                request.externalNo(),
                request.miningArea(),
                request.mass(),
                request.custodian());
        return lineageService.getSample(sample.getExternalNo());
    }

    /** 查询单个样本的质量、保管方等当前状态。 */
    @GetMapping("/samples/{externalNo}")
    public SampleView getSample(@PathVariable String externalNo) {
        return lineageService.getSample(externalNo);
    }

    /** 层级分样。 */
    @PostMapping("/splits")
    @ResponseStatus(HttpStatus.CREATED)
    public SplitEvent split(@RequestBody SplitRequest request) {
        return splitService.split(
                request.eventNo(),
                request.parentExternalNo(),
                request.declaredLossMass(),
                request.children());
    }

    /** 当前保管方发起交接。 */
    @PostMapping("/custody/initiate")
    @ResponseStatus(HttpStatus.CREATED)
    public CustodyEvent initiateCustody(@RequestBody CustodyInitiateRequest request) {
        return custodyService.initiate(
                request.eventNo(),
                request.sampleExternalNo(),
                request.fromCustodian(),
                request.toLab());
    }

    /** 指定接收实验室确认交接。 */
    @PostMapping("/custody/confirm")
    public CustodyEvent confirmCustody(@RequestBody CustodyConfirmRequest request) {
        return custodyService.confirm(request.eventNo(), request.confirmedBy());
    }

    /** 提交检测结果（进入待复核状态）。 */
    @PostMapping("/assays")
    @ResponseStatus(HttpStatus.CREATED)
    public AssayEvent submitAssay(@RequestBody AssaySubmitRequest request) {
        return assayService.submit(
                request.eventNo(),
                request.sampleExternalNo(),
                request.itemCode(),
                request.resultValue(),
                request.unit(),
                request.submittedBy());
    }

    /** 复核待复核结果；复核人必须与原提交人不同，通过后结果生效。 */
    @PostMapping("/assays/reviews")
    @ResponseStatus(HttpStatus.CREATED)
    public ApprovalEvent reviewAssay(@RequestBody ReviewRequest request) {
        return reviewService.reviewSubmission(
                request.approvalNo(),
                request.resultEventNo(),
                request.decision(),
                request.decidedBy(),
                request.comment());
    }

    /** 创建结果更正申请，引用原生效结果并保存旧值/新值/原因/证据。 */
    @PostMapping("/corrections")
    @ResponseStatus(HttpStatus.CREATED)
    public AssayCorrection requestCorrection(@RequestBody CorrectionRequest request) {
        return reviewService.requestCorrection(
                request.correctionNo(),
                request.resultEventNo(),
                request.newValue(),
                request.newUnit(),
                request.reason(),
                request.evidence(),
                request.requestedBy());
    }

    /** 审批更正申请；批准后旧版本被取代并生成新版本。 */
    @PostMapping("/corrections/decisions")
    @ResponseStatus(HttpStatus.CREATED)
    public ApprovalEvent decideCorrection(@RequestBody CorrectionDecisionRequest request) {
        return reviewService.decideCorrection(
                request.approvalNo(),
                request.correctionNo(),
                request.decision(),
                request.decidedBy(),
                request.comment());
    }

    /** 查询某检测项目当前对外有效结果。 */
    @GetMapping("/samples/{externalNo}/results/{itemCode}/effective")
    public CurrentResultView getEffectiveResult(@PathVariable String externalNo,
                                                @PathVariable String itemCode) {
        return resultQueryService.getEffective(externalNo, itemCode);
    }

    /**
     * 查询版本链、当前有效/待复核结果与全部复核记录；
     * 可带 asOf 参数（ISO-8601）还原该时点的有效结果。
     */
    @GetMapping("/samples/{externalNo}/results/{itemCode}")
    public ResultHistoryView getResultHistory(@PathVariable String externalNo,
                                              @PathVariable String itemCode,
                                              @RequestParam(required = false) Instant asOf) {
        return resultQueryService.getHistory(externalNo, itemCode, asOf);
    }

    /** 样本谱系与全部检测项目结果（版本链 + 复核记录）联合查询。 */
    @GetMapping("/samples/{externalNo}/results")
    public SampleResultsLineageView getSampleResultsLineage(@PathVariable String externalNo) {
        return resultQueryService.getSampleResultsLineage(externalNo);
    }

    /** 查询完整谱系（祖先 + 后代 + 事件时间线）。 */
    @GetMapping("/samples/{externalNo}/lineage")
    public LineageView getLineage(@PathVariable String externalNo) {
        return lineageService.getLineage(externalNo);
    }
}
