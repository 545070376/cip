/*
 * sinosoft https://github.com/wliduo
 * Created By sinosoft
 * Date By (2019-05-06 16:41:32)
 */
package io.piccsz.modules.cip.controller;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import io.piccsz.common.annotation.SysLog;
import io.piccsz.common.utils.Constant;
import io.piccsz.common.utils.ExcelUtil;
import io.piccsz.common.utils.ExceptionUtils;
import io.piccsz.common.utils.R;
import io.piccsz.common.validator.ValidatorUtils;
import io.piccsz.common.validator.rule.SurveryorSaveRule;
import io.piccsz.common.validator.rule.SurveryorUpdatePasswordRule;
import io.piccsz.modules.cip.dto.custom.CoDto;
import io.piccsz.modules.cip.form.CoRegisterForm;
import io.piccsz.modules.cip.utils.CodeGenerator;
import io.piccsz.modules.sys.dto.custom.SysUserDto;
import io.piccsz.modules.sys.form.PasswordForm;
import io.piccsz.modules.sys.form.SysLoginForm;
import io.piccsz.modules.sys.service.SysUserService;
import io.piccsz.modules.sys.service.SysUserTokenService;
import org.apache.shiro.crypto.hash.Sha256Hash;
import org.springframework.web.bind.annotation.*;
import org.springframework.beans.factory.annotation.Autowired;
import io.piccsz.modules.sys.controller.AbstractController;
import io.piccsz.modules.cip.dto.custom.CoSurveyorDto;
import io.piccsz.modules.cip.service.ICoSurveyorService;
import io.piccsz.common.dto.ResponseBean;
import io.piccsz.common.exception.CustomException;
import com.github.pagehelper.PageHelper;
import com.github.pagehelper.PageInfo;
import org.springframework.http.HttpStatus;
import org.springframework.web.multipart.MultipartFile;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static io.piccsz.common.utils.ShiroUtils.getUserId;

/**
 * CoSurveyorController
 * @author Generator
 * @date 2019-05-06 16:41:32
 */
@RestController
@RequestMapping("cip/coSurveyor")
public class CoSurveyorController extends AbstractController{

    private final ICoSurveyorService coSurveyorService;

    @Autowired
    private ExcelUtil excelUtil;

    @Autowired
    private SysUserService sysUserService;

    @Autowired
    private SysUserTokenService sysUserTokenService;

    @Autowired
    public CoSurveyorController (ICoSurveyorService coSurveyorService) {
        this.coSurveyorService = coSurveyorService;
    }

    /*********************************************后台调查人员 start******************************************************************/

    /**
     * 列表
     * @author Generator
     * @date 2019-05-06 16:41:32
     */
    @GetMapping
    public ResponseBean list(CoSurveyorDto coSurveyorDto){
        PageHelper.startPage(coSurveyorDto.getPage(),coSurveyorDto.getRows(),"g.operatetimeforhis "+coSurveyorDto.getSord());
        coSurveyorDto.setOperateStatus(Constant.NORMAL_STATUS);
        List<CoSurveyorDto> coSurveyorDtoList = coSurveyorService.findPageInfo(coSurveyorDto);
        PageInfo selectPage = new PageInfo(coSurveyorDtoList);
        Map<String, Object> result = new HashMap<String, Object>(16);
        result.put("count", selectPage.getTotal());
        result.put("data", selectPage.getList());
        return new ResponseBean(HttpStatus.OK.value(),"查询成功(Query was successful)", result);
    }

    /**
     * 调查人员导出(固定列导出)
     * @param request
     * @param response
     * @param resultJson
     * @throws Exception
     */
    @PostMapping(value="/exportExcel")
    public void exportExcel(HttpServletRequest request, HttpServletResponse response, @RequestBody(required = false) Map<String,Object> resultJson){
        // 选中导出,未选中就全表导出
        List<CoSurveyorDto> selectList = JSON.parseArray(resultJson.get("selectListJson").toString(),CoSurveyorDto.class);
        CoSurveyorDto coSurveyorDto = JSON.parseObject(resultJson.get("coSurveyorDto").toString(),CoSurveyorDto.class);
        if(selectList != null && selectList.size() > 0){
            List ccdIds = new ArrayList(selectList.size());
            for(CoSurveyorDto dto:selectList){
                ccdIds.add(dto.getCcsId());
            }
            coSurveyorDto.setCcsIds(ccdIds);
            coSurveyorDto.setOperateStatus(Constant.NORMAL_STATUS);
        }
        List<CoSurveyorDto> coSurveyorDtoList = coSurveyorService.findTranInfoList(coSurveyorDto);
        try {
            excelUtil.downloadExcelByTemplateId(request, response, coSurveyorDtoList, Constant.ExcelTypeCode.SURVEYOR.getCode());
        }catch (IOException | IllegalAccessException e){
            ExceptionUtils.getExceptionStackTraceString(e);
        }
    }

    /*********************************************后台调查人员 end******************************************************************/

    /*********************************************前台调查人员 start******************************************************************/

    /**
     * 列表
     * @author Generator
     * @date 2019-05-06 16:41:32
     */
    @GetMapping("/fList")
    public ResponseBean fList(CoSurveyorDto coSurveyorDto){
        List<CoSurveyorDto> coSurveyorDtoList = coSurveyorService.fList(coSurveyorDto);
        PageInfo selectPage = new PageInfo(coSurveyorDtoList);
        Map<String, Object> result = new HashMap<String, Object>(16);
        result.put("count", selectPage.getTotal());
        result.put("data", selectPage.getList());
        return new ResponseBean(HttpStatus.OK.value(),"查询成功(Query was successful)", result);
    }

    /**
     * 信息
     * @author Generator
     * @date 2019-05-06 16:41:32
     */
    @GetMapping("/{id}")
    public ResponseBean info(@PathVariable("id") String id){
        CoSurveyorDto coSurveyorDto = coSurveyorService.selectById(id);
        if (coSurveyorDto == null) {
            throw new CustomException("查询失败(Query Failure)");
        }
        SysUserDto sysUserDto = sysUserService.selectById(coSurveyorDto.getUserId());
        coSurveyorDto.setUserName(sysUserDto.getUserName());
        return new ResponseBean(HttpStatus.OK.value(), "查询成功(Query was successful)", coSurveyorDto);
    }

    /**
     * 获取调查人员代码
     * @return
     */
    @GetMapping("/getSurveyorCode")
    public ResponseBean getSurveyorCode(){
        return coSurveyorService.getSurveyorCode();
    }

    /**
     * 校验调查员用户名
     * @param coSurveyorDto
     * @return
     */
    @PostMapping("/verifyUserName")
    public ResponseBean verifyUserName(@RequestBody CoSurveyorDto coSurveyorDto) {
        return coSurveyorService.verifyUserName(coSurveyorDto);
    }

    /**
     * 校验调查员手机号
     * @param coSurveyorDto
     * @return
     */
    @PostMapping("/verifyMobile")
    public ResponseBean verifyMobile(@RequestBody CoSurveyorDto coSurveyorDto) {
        return coSurveyorService.verifyMobile(coSurveyorDto);
    }

    /**
     * 新增
     * @author Generator
     * @date 2019-05-06 16:41:32
     */
    @PostMapping
    public ResponseBean save(@RequestBody CoSurveyorDto coSurveyorDto) throws Exception {
        ValidatorUtils.validateEntity(coSurveyorDto, SurveryorSaveRule.class);
        return coSurveyorService.save(coSurveyorDto);
    }


    /**
     * 更新
     * @author Generator
     * @date 2019-05-06 16:41:32
     */
    @PutMapping
    public ResponseBean update(@RequestBody CoSurveyorDto coSurveyorDto) throws Exception{
        return coSurveyorService.update(coSurveyorDto);
    }


    /**
     * 删除(逻辑)
     * @author Generator
     * @date 2019-05-06 16:41:32
     */
    @DeleteMapping("/{id}")
    public ResponseBean delete(@PathVariable("id") String id){
        CoSurveyorDto coSurveyorDto = coSurveyorService.selectById(id);
        // 查询对象为空
        if (coSurveyorDto == null) {
            throw new CustomException("删除失败，ID不存在(Deletion Failed. ID does not exist.)");
        }
        coSurveyorDto.setOperateStatus(Constant.DELETE_STATUS);
        Boolean flag = coSurveyorService.updateById(coSurveyorDto);
        if (!flag) {
            throw new CustomException("删除失败，ID不存在(Deletion Failed. ID does not exist.)");
        }
        return new ResponseBean(HttpStatus.OK.value(), "删除成功(Delete Success)", null);
    }

    /**
     * 调查人员导出(固定列导出)(前台)
     * @param request
     * @param response
     * @param resultJson
     * @throws Exception
     */
    @PostMapping(value="/fExportExcel")
    public void fExportExcel(HttpServletRequest request, HttpServletResponse response, @RequestBody(required = false) Map<String,Object> resultJson){
        // 选中导出,未选中就全表导出
        List<CoSurveyorDto> selectList = JSON.parseArray(resultJson.get("selectListJson").toString(),CoSurveyorDto.class);
        CoSurveyorDto coSurveyorDto = JSON.parseObject(resultJson.get("coSurveyorDto").toString(),CoSurveyorDto.class);
        if(selectList != null && selectList.size() > 0){
            List ccdIds = new ArrayList(selectList.size());
            for(CoSurveyorDto dto:selectList){
                ccdIds.add(dto.getCcsId());
            }
            coSurveyorDto.setCcsIds(ccdIds);
            coSurveyorDto.setOperateStatus(Constant.NORMAL_STATUS);
        }
        List<CoSurveyorDto> coSurveyorDtoList = coSurveyorService.findTranInfoQTList(coSurveyorDto);
        try {
            excelUtil.downloadExcelByTemplateId(request, response, coSurveyorDtoList, Constant.ExcelTypeCode.SURVEYOR_QT.getCode());
        }catch (IOException | IllegalAccessException e){
            ExceptionUtils.getExceptionStackTraceString(e);
        }
    }

    /**
     * 调查人员导入
     * @param file
     * @return
     */
    @PostMapping("/fImportExcel")
    public ResponseBean fImportExcel(@RequestParam("file") MultipartFile file) throws Exception {
        JSONObject result = coSurveyorService.batchImportCoSurveyorData(file,getUserId().toString());
        return new ResponseBean(HttpStatus.OK.value(),"导入完成 importSuccess",result);
    }

    /**
     * 调查员登录
     * @param form
     * @return
     * @throws IOException
     */
    @PostMapping("/login")
    public Map<String, Object> login(@RequestBody SysLoginForm form) throws IOException {
        //用户信息
        SysUserDto user = coSurveyorService.queryUserByUserCode(form.getUserCode());
        //账号不存在、密码错误
        if(user == null || !user.getPassword().equals(new Sha256Hash(form.getPassword(), user.getSalt()).toHex())) {
            return R.error("账号或密码不正确");
        }

        //账号锁定
        if("0".equals(user.getValidInd())){
            return R.error("账号已被锁定,请联系管理员");
        }

        //生成token，并保存到数据库
        R r = sysUserTokenService.createToken(user.getUserId());
        return r;
    }

    /**
     * 调查员修改密码
     * @param form
     * @return
     */
    @PostMapping("/updatePassword")
    public ResponseBean password(@RequestBody PasswordForm form) {
        ValidatorUtils.validateEntity(form, SurveryorUpdatePasswordRule.class);
        return coSurveyorService.updatePassword(form);
    }

    /**
     * 调查人员导入模板下载
     * @param response
     */
    @GetMapping("/downCoSurveyorTpl")
    public void downCoSurveyorTpl(HttpServletRequest request, HttpServletResponse response){
        coSurveyorService.downCoSurveyorTpl(request, response);
    }

    /*********************************************前台调查人员 end******************************************************************/
}