package com.chris64233.cc.assaychain.api;

import com.chris64233.cc.assaychain.api.dto.AssaySubmitRequest;
import com.chris64233.cc.assaychain.api.dto.CustodyConfirmRequest;
import com.chris64233.cc.assaychain.api.dto.CustodyInitiateRequest;
import com.chris64233.cc.assaychain.api.dto.ReceiveRequest;
import com.chris64233.cc.assaychain.api.dto.SplitRequest;
import com.chris64233.cc.assaychain.domain.AssayEvent;
import com.chris64233.cc.assaychain.domain.CustodyEvent;
import com.chris64233.cc.assaychain.domain.Sample;
import com.chris64233.cc.assaychain.domain.SplitEvent;
import com.chris64233.cc.assaychain.service.AssayService;
import com.chris64233.cc.assaychain.service.CustodyService;
import com.chris64233.cc.assaychain.service.LineageService;
import com.chris64233.cc.assaychain.service.LineageView;
import com.chris64233.cc.assaychain.service.ReceptionService;
import com.chris64233.cc.assaychain.service.SampleView;
import com.chris64233.cc.assaychain.service.SplitService;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
public class AssayChainController {

    private final ReceptionService receptionService;
    private final SplitService splitService;
    private final CustodyService custodyService;
    private final AssayService assayService;
    private final LineageService lineageService;

    public AssayChainController(ReceptionService receptionService,
                                SplitService splitService,
                                CustodyService custodyService,
                                AssayService assayService,
                                LineageService lineageService) {
        this.receptionService = receptionService;
        this.splitService = splitService;
        this.custodyService = custodyService;
        this.assayService = assayService;
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

    /** 提交检测结果。 */
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

    /** 查询完整谱系（祖先 + 后代 + 事件时间线）。 */
    @GetMapping("/samples/{externalNo}/lineage")
    public LineageView getLineage(@PathVariable String externalNo) {
        return lineageService.getLineage(externalNo);
    }
}
