package io.piccsz.modules.cip.controller;

import com.github.pagehelper.PageHelper;
import com.github.pagehelper.PageInfo;
import io.piccsz.common.dto.ResponseBean;
import io.piccsz.common.validator.ValidatorUtils;
import io.piccsz.common.validator.rule.BidApplyDelayRule;
import io.piccsz.common.validator.rule.ConfirmEntrustLetterRule;
import io.piccsz.modules.cip.dto.custom.BusFileDto;
import io.piccsz.modules.cip.dto.custom.CaseDto;
import io.piccsz.modules.cip.dto.custom.CaseSolDto;
import io.piccsz.modules.cip.dto.custom.CaseSurveyorDto;
import io.piccsz.modules.cip.service.ICaseService;
import io.piccsz.modules.cip.service.ICaseSolService;
import io.piccsz.modules.cip.service.ICaseSurveyorService;
import io.piccsz.modules.sys.controller.AbstractController;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * @Author: bin.chen
 * @Date: 2019/5/28
 * @Description: 投标清单
 */
@RestController
@RequestMapping("cip/bidCase")
public class BidCaseController extends AbstractController {

    private final ICaseService caseService;

    private final ICaseSolService caseSolService;

    private final ICaseSurveyorService caseSurveyorService;

    @Autowired
    public BidCaseController(ICaseService caseService, ICaseSolService caseSolService, ICaseSurveyorService caseSurveyorService) {
        this.caseService = caseService;
        this.caseSolService = caseSolService;
        this.caseSurveyorService = caseSurveyorService;
    }

    /*********************************************前台管理系统 start******************************************************************/

    /**
     * 投标清单列表
     * @param caseDto
     * @return
     */
    @GetMapping("/fList")
    public ResponseBean bidDetailList(CaseDto caseDto){
        List<CaseDto> caseDtoList = caseService.findBidDetailList(caseDto);
        PageInfo selectPage = new PageInfo(caseDtoList);
        Map<String, Object> result = new HashMap<String, Object>(16);
        result.put("count", selectPage.getTotal());
        result.put("data", selectPage.getList());
        return new ResponseBean(HttpStatus.OK.value(),"查询成功(Query was successful)", result);
    }

    /**
     * 投标清单详情
     * @param caseDto
     * @return
     */
    @GetMapping("/fInfo")
    public ResponseBean bidDetailInfo(CaseDto caseDto){
        CaseDto c = caseService.queryBidDetailInfo(caseDto);
        return new ResponseBean(HttpStatus.OK.value(),"查询成功(Query was successful)", c);
    }

    /**
     * 上传方案
     * @param caseSolDto
     * @return
     * @throws Exception
     */
    @PostMapping("/uploadPlan")
    public ResponseBean uploadPlan(@RequestBody CaseSolDto caseSolDto) throws Exception {
        return caseSolService.uploadPlan(caseSolDto);
    }

    /**
     * 申请延时
     * @param caseSolDto
     * @return
     */
    @PostMapping("/applyDelay")
    public ResponseBean applyDelay(@RequestBody CaseSolDto caseSolDto) throws Exception{
        ValidatorUtils.validateEntity(caseSolDto, BidApplyDelayRule.class);
        return caseSolService.applyDelay(caseSolDto);
    }

    /**
     * 获取委托函
     * @param caseSurveyorDto
     * @return
     */
    @GetMapping("/getEntrustLetter")
    public ResponseBean getEntrustLetter(CaseSurveyorDto caseSurveyorDto){
        return caseSurveyorService.getEntrustLetter(caseSurveyorDto);
    }

    /**
     * 确认委托函
     * @param caseSurveyorDto
     * @return
     */
    @PostMapping("/confirmEntrustLetter")
    public ResponseBean confirmEntrustLetter(@RequestBody CaseSurveyorDto caseSurveyorDto) throws Exception {
        ValidatorUtils.validateEntity(caseSurveyorDto, ConfirmEntrustLetterRule.class);
        return caseSurveyorService.confirmEntrustLetter(caseSurveyorDto);
    }

    /*********************************************前台管理系统 end********************************************************************/
}
