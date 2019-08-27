/*
 * sinosoft https://github.com/dolyw
 * Created By sinosoft
 * Date By (2019-05-06 16:40:51)
 */
package io.piccsz.modules.cip.controller;
import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.baomidou.mybatisplus.mapper.EntityWrapper;
import io.piccsz.common.utils.*;
import io.piccsz.modules.cip.dto.custom.*;
import io.piccsz.modules.cip.service.*;
import io.piccsz.modules.oss.entity.FileInfo;
import io.piccsz.modules.oss.util.AliyunCloudUtil;
import io.piccsz.modules.sys.dto.custom.SysExcelTemplateDetailDto;
import io.piccsz.modules.sys.service.SysUserService;
import org.springframework.web.bind.annotation.*;
import org.springframework.beans.factory.annotation.Autowired;
import io.piccsz.modules.sys.controller.AbstractController;

import com.github.pagehelper.PageHelper;
import com.github.pagehelper.PageInfo;
import io.piccsz.common.dto.ResponseBean;
import io.piccsz.common.exception.CustomException;
import io.piccsz.common.utils.Constant;
import io.piccsz.common.utils.ToolsUtils;
import io.piccsz.modules.cip.dto.custom.CaseSolDto;
import io.piccsz.modules.cip.dto.custom.CoDto;
import io.piccsz.modules.cip.service.ICaseSolService;
import io.piccsz.modules.sys.controller.AbstractController;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import io.piccsz.modules.cip.dto.common.DynamicFieldDto;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.ArrayList;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * CaseController
 * @author Generator
 * @date 2019-05-06 16:40:51
 */
@RestController
@RequestMapping("cip/case")
public class CaseController extends AbstractController{

    private final ICaseService caseService;

    private final ICaseSolService caseSolService;

    @Autowired
    private SysUserService sysUserService;

    @Autowired
    private IBusFileService busFileService;

    @Autowired
    private ExcelUtil excelUtil;

    @Autowired
    private ITmplDimService tmplDimService;

    private final ICoService coService;

    @Autowired
    private IBusAuditService busAuditService;

    @Autowired
    private ICaseViewService caseViewService;

    @Autowired
    public CaseController (ICaseService caseService, ICaseSolService caseSolService, ICoService coService) {
        this.caseService = caseService;
        this.caseSolService = caseSolService;
        this.coService = coService;
    }

    /*********************************************后台管理系统 start******************************************************************/

    /**
     * 总案件清单列表
     * @author Generator
     * @date 2019-05-06 16:40:51
     */
    @GetMapping
    public ResponseBean list(CaseDto caseDto){
        PageHelper.startPage(caseDto.getPage(),caseDto.getRows(),"t.operatetimeforhis "+caseDto.getSord());
        caseDto.setOperateStatus("1");
        List<CaseDto> caseDtoList = caseService.findAllCasePageInfo(caseDto);
        PageInfo selectPage = new PageInfo(caseDtoList);
        Map<String, Object> result = new HashMap<String, Object>(16);
        result.put("count", selectPage.getTotal());
        result.put("data", selectPage.getList());
        return new ResponseBean(HttpStatus.OK.value(),"查询成功(Query was successful)", result);
    }

    /**
     * 查看案件信息
     * @author Generator
     * @date 2019-05-06 16:40:51
     */
    @GetMapping("/{caseId}")
    public ResponseBean info(@PathVariable("caseId") Long caseId) throws Exception {
        if (caseId == null) {
            return new ResponseBean(HttpStatus.BAD_REQUEST.value(),"caseId为空", null);
        }

        CaseDto caseDto = caseViewService.findCaseInfo(caseId);
        CaseSurveyDto caseSurveyDto = caseViewService.findCaseSurveyInfo(caseId);
        CaseSolDto caseSolDto = caseViewService.findCaseSolInfo(caseId);
        List<BusDimValDto> busDimValDtos = caseViewService.findBusDimValInfo(caseId);

        Map<String, Object> result = new HashMap<String, Object>(16);
        result.put("caseDto", caseDto);
        result.put("caseSurveyDto", caseSurveyDto);
        result.put("caseSolDto", caseSolDto);
        result.put("busDimValDtos", busDimValDtos);

        return new ResponseBean(HttpStatus.OK.value(), "查询成功(Query was successful)", result);
    }

    /**
     * 新增
     * @author Generator
     * @date 2019-05-06 16:40:51
     */
    @PostMapping
    public ResponseBean save(@RequestBody CaseDto caseDto) throws Exception{

        return caseService.insertCase(caseDto);
    }

    /**
     * 案件发布
     * @param caseId
     * @return
     * @throws Exception
     */
    @PutMapping("/publish/{id}")
    public ResponseBean publish(@PathVariable("id")  Long caseId)throws Exception{
        CaseDto caseDto = caseService.selectById(caseId);
        if (caseDto == null) {
            throw new CustomException("发布失败，ID不存在(Publish Failed. ID does not exist.)");
        }
        caseDto.setInterState(Constant.CaseInterState.PUBLISHED.getCode());
        ToolsUtils.setUpdateUserAndDate(caseDto);
        Boolean flag = caseService.updateById(caseDto);

        if (!flag) {
            throw new CustomException("发布失败，ID不存在(Publish Failed. ID does not exist.)");
        }
        return new ResponseBean(HttpStatus.OK.value(), "发布成功", null);
    }

    /**
     * 案件提交
     * @param caseId
     * @return
     * @throws Exception
     */
    @PutMapping("/submitCase/{caseId}")
    public ResponseBean submitCase(@PathVariable("caseId")  Long caseId)throws Exception{
        CaseDto caseDto = caseService.selectById(caseId);
        if (caseDto == null) {
            throw new CustomException("提交失败，ID不存在(Submit Failed. ID does not exist.)");
        }
        caseDto.setInterState(Constant.CaseInterState.COMMITTED.getCode());
        caseDto.setAuditState(Constant.AuditStatus.TOBEAUDIT.getCode());
        ToolsUtils.setUpdateUserAndDate(caseDto);
        Boolean flag = caseService.updateById(caseDto);

        CaseSolDto caseSolDto = caseSolService.selectOne(new EntityWrapper<CaseSolDto>().eq("case_id", caseId).eq("valid_ind", Constant.VALID_IND).eq("operate_status", Constant.NORMAL_STATUS));
        if (ToolsUtils.isEmpty(caseSolDto)) {
            throw new CustomException("提交失败，该案件不存在调查机构(Submit Failed)");
        }
        busAuditService.delegateCoAudit(caseSolDto);
        if (!flag) {
            throw new CustomException("提交失败，ID不存在(Publish Failed. ID does not exist.)");
        }
        return new ResponseBean(HttpStatus.OK.value(), "提交成功", null);
    }

    /**
     * 更新
     * @author Generator
     * @date 2019-05-06 16:40:51
     */
    @PutMapping
    public ResponseBean update(@RequestBody CaseDto caseDto) throws  Exception{
        /*caseService.updateCase(caseDto);
        if (!flag) {
            throw new CustomException("更新失败(Update Failure)");
        }*/
        return caseService.updateCase(caseDto);
    }


    /**
     * 删除(逻辑)
     * @author Generator
     * @date 2019-05-06 16:40:51
     */
    @DeleteMapping("/{id}")
    public ResponseBean delete(@PathVariable("id") String id){
        CaseDto caseDto = caseService.selectById(id);
        // 查询对象为空
        if (caseDto == null) {
            throw new CustomException("删除失败，ID不存在(Deletion Failed. ID does not exist.)");
        }
        caseDto.setOperateStatus("0");
        Boolean flag = caseService.updateById(caseDto);
        if (!flag) {
            throw new CustomException("删除失败，ID不存在(Deletion Failed. ID does not exist.)");
        }
        return new ResponseBean(HttpStatus.OK.value(), "删除成功(Delete Success)", null);
    }

    @PostMapping("/upload")
    public ResponseBean upload(@RequestParam("file") MultipartFile multipartFile) {
        ResponseBean responseBean = null;
        try {
            FileInfo fileInfo = AliyunCloudUtil.upload(multipartFile);
            responseBean = new ResponseBean(HttpStatus.OK.value(), "上传成功", fileInfo);
        } catch (Exception e) {
            responseBean = new ResponseBean(HttpStatus.OK.value(), "上传失败", "");
        }

        return responseBean;
    }

    /**
     * @throws Exception
     * @Desc 总案件清单导出Excel
     * @Author lming
     * @Date 2019/6/1
     */
    @PostMapping(value="/exportExcel")
    public void exportExcel(HttpServletRequest request, HttpServletResponse response,  @RequestBody(required = false) Map<String,Object> resultJson) {
        // 选中导出,未选中就全表导出
        List<CaseDto> selectList = JSON.parseArray(resultJson.get("selectListJson").toString(),CaseDto.class);
       // CaseDto caseDto = JSON.parseObject(JSON.toJSONString(resultJson.get("caseDto")),CaseDto.class);
        CaseDto caseDto = JSON.parseObject(resultJson.get("caseDto").toString(),CaseDto.class);
        if(selectList != null && selectList.size() > 0){
            List caseIds = new ArrayList(selectList.size());
            for(CaseDto dto:selectList){
                caseIds.add(dto.getCaseId());
            }
            caseDto.setCaseIds(caseIds);
            caseDto.setOperateStatus(Constant.NORMAL_STATUS);
        }
        List<CaseDto> caseDtoList = caseService.findAllCasePageInfo(caseDto);

        // 构建动态项Excel单元项
        List<SysExcelTemplateDetailDto> excelCells = new ArrayList<>();
        List<DynamicFieldDto> dynamicFieldDtos = tmplDimService.getTmplDynamicFieldDto();
        dynamicFieldDtos.forEach(dynamicFieldDto -> {
            SysExcelTemplateDetailDto excelCell = new SysExcelTemplateDetailDto();
            excelCell.setExcelName(dynamicFieldDto.getFiledColumnName());
            excelCell.setName(dynamicFieldDto.getFiledColumn());
            excelCells.add(excelCell);
        });
        List<JSONObject> data = new ArrayList<>();
        caseDtoList.forEach(record -> {
            JSONObject tmpObj = (JSONObject) JSONObject.toJSON(record);
            data.add(tmpObj);
        });


        try {
            excelUtil.downloadExcelByTemplateIdWithJSONObject(request, response, data, Constant.ExcelTypeCode.ALLCASE_CODE.getCode(), excelCells);
        }catch (IOException | IllegalAccessException e){
            ExceptionUtils.getExceptionStackTraceString(e);
        }
    }


    /*********************************************后台管理系统 end********************************************************************/

    /*********************************************前台管理系统 start******************************************************************/

    /**
     * 首页案件列表
     * @param caseDto
     * @return
     */
    @GetMapping("/index")
    public ResponseBean index(CaseDto caseDto){
        List<CaseDto> caseDtoList = caseService.findIndexPageInfo(caseDto);
        PageInfo selectPage = new PageInfo(caseDtoList);
        Map<String, Object> result = new HashMap<String, Object>(16);
        result.put("count", selectPage.getTotal());
        result.put("data", selectPage.getList());
        return new ResponseBean(HttpStatus.OK.value(),"查询成功(Query was successful)", result);
    }

    /**
     * 首页案件列表（登录）
     * @param caseDto
     * @return
     */
    @GetMapping("/indexForLogin")
    public ResponseBean indexForLogin(CaseDto caseDto){
        List<CaseDto> caseDtoList = caseService.findIndexForLoginPageInfo(caseDto);
        PageInfo selectPage = new PageInfo(caseDtoList);
        Map<String, Object> result = new HashMap<String, Object>(16);
        result.put("count", selectPage.getTotal());
        result.put("data", selectPage.getList());
        return new ResponseBean(HttpStatus.OK.value(),"查询成功(Query was successful)", result);
    }

    /**
     * 首页案件详情
     * @param caseDto
     * @return
     */
    @GetMapping("/indexDetailInfo")
    public ResponseBean indexCaseDetailInfo(CaseDto caseDto){
        CaseDto c = caseService.queryIndexDetailInfo(caseDto);
        return new ResponseBean(HttpStatus.OK.value(),"查询成功(Query was successful)", c);
    }

    /*********************************************前台管理系统 end********************************************************************/


    /*********************************************移动端管理系统 start****************************************************************/


    /*********************************************移动端管理系统 end******************************************************************/





}