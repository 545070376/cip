/*
 * sinosoft https://github.com/wliduo
 * Created By sinosoft
 * Date By (2019-05-06 16:41:32)
 */
package io.piccsz.modules.cip.service.impl;

import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.baomidou.mybatisplus.mapper.EntityWrapper;
import com.github.pagehelper.PageHelper;
import io.piccsz.common.dto.ResponseBean;
import io.piccsz.common.exception.ExcelException;
import io.piccsz.common.mail.MailSendUtils;
import io.piccsz.common.sms.SmsUtils;
import io.piccsz.common.utils.*;
import io.piccsz.modules.cip.dao.CoDao;
import io.piccsz.modules.cip.dto.common.CoSurveyorRowName;
import io.piccsz.modules.cip.dto.custom.CoDto;
import io.piccsz.modules.cip.utils.CodeGenerator;
import io.piccsz.modules.cip.utils.ErrorListUtil;
import io.piccsz.modules.sys.dao.*;
import io.piccsz.modules.sys.dto.custom.*;
import io.piccsz.modules.sys.form.PasswordForm;
import io.piccsz.modules.sys.service.SysConfigService;
import org.apache.commons.lang.RandomStringUtils;
import org.apache.shiro.crypto.hash.Sha256Hash;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import com.baomidou.mybatisplus.service.impl.ServiceImpl;
import io.piccsz.modules.cip.dao.CoSurveyorDao;
import io.piccsz.modules.cip.dto.custom.CoSurveyorDto;
import io.piccsz.modules.cip.service.ICoSurveyorService;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import static io.piccsz.common.utils.ShiroUtils.getUserId;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * CoSurveyorServiceImpl
 * @author Generator
 * @date 2019-05-06 16:41:32
 */
@Service("coSurveyorService")
public class CoSurveyorServiceImpl extends ServiceImpl<CoSurveyorDao,CoSurveyorDto> implements ICoSurveyorService {

    @Autowired
    private CoSurveyorDao coSurveyorDao;

    @Autowired
    private SysUserDao sysUserDao;

    @Autowired
    private SysValidCodeDao sysValidCodeDao;

    @Autowired
    private SysCaptchaDao sysCaptchaDao;

    @Autowired
    private CoDao coDao;

    @Autowired
    private SysUserRoleDao sysUserRoleDao;

    @Autowired
    private SysDictDataDao sysDictDataDao;

    @Autowired
    private SysConfigService sysConfigService;

    /**
     * 列表
     * @param coSurveyorDto
     * @return java.util.List<io.piccsz.modules.cip.dto.custom.CoSurveyorDto;>
     * @author Generator
     * @date 2019-05-06 16:41:32
     */
    @Override
    public List<CoSurveyorDto> findPageInfo(CoSurveyorDto coSurveyorDto) {
        return coSurveyorDao.findPageInfo(coSurveyorDto);
    }

    /**
     * 调查人员列表（前台）
     * @param coSurveyorDto
     * @return
     */
    @Override
    public List<CoSurveyorDto> fList(CoSurveyorDto coSurveyorDto) {
        CoDto coDto = new CoDto();
        coDto.setUserId(getUserId());
        coDto = coDao.selectOne(coDto);
        coSurveyorDto.setCoId(coDto.getCoId());
        PageHelper.startPage(coSurveyorDto.getPage(),coSurveyorDto.getRows(),"g.operatetimeforhis "+coSurveyorDto.getSord());
        coSurveyorDto.setOperateStatus(Constant.NORMAL_STATUS);
        return coSurveyorDao.fList(coSurveyorDto);
    }

    /**
     * 获取调查人员代码
     * @return
     */
    @Override
    public ResponseBean getSurveyorCode() {
        CoDto coDto = new CoDto();
        coDto.setUserId(getUserId());
        coDto = coDao.selectOne(coDto);
        String code = CodeGenerator.getInstance().generateSurveyorCode(coDto.getCoCode());
        return new ResponseBean(HttpStatus.OK.value(), "查询成功(Query was successful)", code);
    }

    /**
     * 校验调查员用户名
     * @param coSurveyorDto
     * @return
     */
    @Override
    public ResponseBean verifyUserName(CoSurveyorDto coSurveyorDto) {
        SysUserDto user= new SysUserDto();
        user.setUserName(coSurveyorDto.getUserName());
        user = sysUserDao.selectOne(user);
        if(user != null) {
            return ResponseBean.error("该用户名已被注册");
        }

        return new ResponseBean(HttpStatus.OK.value(), "校验成功", null);
    }

    /**
     * 校验调查员手机号
     * @param coSurveyorDto
     * @return
     */
    @Override
    public ResponseBean verifyMobile(CoSurveyorDto coSurveyorDto) {
        SysUserDto user= new SysUserDto();
        user.setMobile(coSurveyorDto.getMobile());
        user = sysUserDao.selectOne(user);
        if(user != null) {
            return ResponseBean.error("该手机号已被注册");
        }

        return new ResponseBean(HttpStatus.OK.value(), "校验成功", null);
    }

    /**
     * 保存调查员
     * @param coSurveyorDto
     * @return
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public ResponseBean save(CoSurveyorDto coSurveyorDto) throws Exception {
        // 设置角色
        SysDictDataDto sysDictDataDto = new SysDictDataDto();

        // 设置邮件内容
        String serverUrl = sysConfigService.getValue(Constant.SERVER_URL);
        String outLoginUrl = sysConfigService.getValue(Constant.OUT_LOGIN_URL);
        String mobileLoginUrl = sysConfigService.getValue(Constant.MOBILE_LOGIN_URL);
        StringBuilder mailConent = new StringBuilder();

        // 保存用户信息
        SysUserDto user = new SysUserDto();
        user.setUserCode(coSurveyorDto.getUserName());
        user.setUserName(coSurveyorDto.getUserName());
        user.setMobile(coSurveyorDto.getMobile());
        user.setValidInd(coSurveyorDto.getValidInd());
        if(coSurveyorDto.getEeType().equals(Constant.SurveyorEeType.STAFFADMINISTRATOR.getCode())) {
            user.setUserType(Constant.userType.COUSER.getCode());
            sysDictDataDto.setDictType(Constant.SysRole.COROLE.getCode());
            mailConent.append("你的注册账户登录用户").append(coSurveyorDto.getUserName()).append(",用户密码").append(Constant.SURVEYOR_INIT_PWD)
                    .append(",登录地址;").append(serverUrl).append("/").append(outLoginUrl);
        }else if (coSurveyorDto.getEeType().equals(Constant.SurveyorEeType.ORDINARYSTAFF.getCode())) {
            user.setUserType(Constant.userType.ORDINARYSTAFF.getCode());
            sysDictDataDto.setDictType(Constant.SysRole.ORDINARYSTAFFROLE.getCode());
            mailConent.append("你的注册账户登录用户").append(coSurveyorDto.getUserName()).append(",用户密码").append(Constant.SURVEYOR_INIT_PWD)
                    .append(",登录地址;").append(serverUrl).append("/").append(outLoginUrl);
        } else if (coSurveyorDto.getEeType().equals(Constant.SurveyorEeType.SURVEYOR.getCode())) {
            user.setUserType(Constant.userType.SURVEYOR.getCode());
            sysDictDataDto.setDictType(Constant.SysRole.SURVEYORROLE.getCode());
            mailConent.append("你的注册账户登录用户").append(coSurveyorDto.getUserName()).append(",用户密码").append(Constant.SURVEYOR_INIT_PWD)
                    .append(",登录地址;").append(serverUrl).append("/").append(mobileLoginUrl);
        }

        //sha256加密
        String salt = RandomStringUtils.randomAlphanumeric(20);
        user.setPassword(new Sha256Hash(Constant.SURVEYOR_INIT_PWD, salt).toHex());
        user.setSalt(salt);
        ToolsUtils.setUserAndDate(user);
        sysUserDao.insert(user);

        // 保存调查员信息
        CoDto coDto = new CoDto();
        coDto.setUserId(getUserId());
        coDto = coDao.selectOne(coDto);
        coSurveyorDto.setUserId(user.getUserId());
        coSurveyorDto.setCoId(coDto.getCoId());
        ToolsUtils.setUserAndDate(coSurveyorDto);
        coSurveyorDao.insert(coSurveyorDto);

        // 给调查人员配置角色
        sysDictDataDto = sysDictDataDao.selectOne(sysDictDataDto);
        SysUserRoleDto sysUserRoleDto = new SysUserRoleDto();
        sysUserRoleDto.setUserId(user.getUserId());
        sysUserRoleDto.setRoleId(Long.valueOf(sysDictDataDto.getDictCode()));
        ToolsUtils.setUserAndDate(sysUserRoleDto);
        sysUserRoleDao.insert(sysUserRoleDto);

        // 发送邮件
        MailSendUtils.sendEmail(coSurveyorDto.getEmail(),"调查平台消息提醒", mailConent.toString(), null);

        // 短信通知
        SmsUtils.sendShortMessage(mailConent.toString(), coSurveyorDto.getMobile());
        return new ResponseBean(HttpStatus.OK.value(),"保存成功", null);
    }

    /**
     * 更新调查员
     * @param coSurveyorDto
     * @return
     * @throws Exception
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public ResponseBean update(CoSurveyorDto coSurveyorDto) throws Exception {
        // 设置角色
        SysDictDataDto sysDictDataDto = new SysDictDataDto();

        // 更新用户信息
        SysUserDto user = new SysUserDto();
        user.setUserId(coSurveyorDto.getUserId());
        user = sysUserDao.selectOne(user);
        if(coSurveyorDto.getEeType().equals(Constant.SurveyorEeType.STAFFADMINISTRATOR.getCode())) {
            user.setUserType(Constant.userType.COUSER.getCode());
            sysDictDataDto.setDictType(Constant.SysRole.COROLE.getCode());
        } else if(coSurveyorDto.getEeType().equals(Constant.SurveyorEeType.ORDINARYSTAFF.getCode())) {
            user.setUserType(Constant.userType.ORDINARYSTAFF.getCode());
            sysDictDataDto.setDictType(Constant.SysRole.ORDINARYSTAFFROLE.getCode());
        } else if (coSurveyorDto.getEeType().equals(Constant.SurveyorEeType.SURVEYOR.getCode())) {
            user.setUserType(Constant.userType.SURVEYOR.getCode());
            sysDictDataDto.setDictType(Constant.SysRole.SURVEYORROLE.getCode());
        }
        user.setMobile(coSurveyorDto.getMobile());
        user.setValidInd(coSurveyorDto.getValidInd());
        ToolsUtils.setUpdateUserAndDate(user);
        sysUserDao.updateById(user);

        // 更新调查员信息
        ToolsUtils.setUpdateUserAndDate(coSurveyorDto);
        coSurveyorDao.updateById(coSurveyorDto);

        // 更新角色
        sysDictDataDto = sysDictDataDao.selectOne(sysDictDataDto);
        SysUserRoleDto sysUserRoleDto = new SysUserRoleDto();
        sysUserRoleDto.setUserId(user.getUserId());
        sysUserRoleDto = sysUserRoleDao.selectOne(sysUserRoleDto);
        sysUserRoleDto.setRoleId(Long.valueOf(sysDictDataDto.getDictCode()));
        ToolsUtils.setUpdateUserAndDate(sysUserRoleDto);
        sysUserRoleDao.updateById(sysUserRoleDto);
        return new ResponseBean(HttpStatus.OK.value(),"更新成功", null);
    }

    /**
     * 导出查询翻译
     * @param coSurveyorDto
     * @return
     */
    @Override
    public List<CoSurveyorDto> findTranInfoList(CoSurveyorDto coSurveyorDto) {
        return coSurveyorDao.findTranInfoList(coSurveyorDto);
    }

    /**
     * 根据工号，查询调查员用户
     * @param userCode
     * @return
     */
    @Override
    public SysUserDto queryUserByUserCode(String userCode) {
        SysUserDto userDto = new SysUserDto();
        userDto.setUserCode(userCode);
        userDto.setUserType(Constant.userType.SURVEYOR.getCode());
        userDto = sysUserDao.selectOne(userDto);
        return userDto;
    }

    /**
     * 修改密码
     * @param form
     * @return
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public ResponseBean updatePassword(PasswordForm form) {
        SysCaptchaDto captchaEntity = new SysCaptchaDto();
        captchaEntity.setUuid(form.getCaptchaUuid());
        captchaEntity = sysCaptchaDao.selectOne(captchaEntity);
        if(captchaEntity == null){
            return ResponseBean.error("图片验证码不正确");
        }

        // 删除图片验证码
        sysCaptchaDao.deleteById(form.getCaptchaUuid());

        if(!captchaEntity.getCode().equalsIgnoreCase(form.getCaptcha()) && captchaEntity.getExpireTime().getTime() >= System.currentTimeMillis()){
            return ResponseBean.error("图片验证码不正确");
        }

        SysValidCodeDto codeDto = new SysValidCodeDto();
        codeDto.setUuid(form.getValidCodeUuid());
        codeDto.setMobile(form.getMobile());
        codeDto.setCode(form.getValidCode());
        codeDto = sysValidCodeDao.selectOne(codeDto);
        if (codeDto == null) {
            return ResponseBean.error("短信验证码不正确");
        }

        if(codeDto.getExpireTime().getTime() < System.currentTimeMillis()){
            return new ResponseBean(HttpStatus.OK.value(), "短信验证码过期了", null);
        }

        SysUserDto userDto = new SysUserDto();
        userDto.setUserCode(form.getUserName());
        userDto = sysUserDao.selectOne(userDto);
        if(userDto == null || !userDto.getPassword().equals(new Sha256Hash(form.getPassword(), userDto.getSalt()).toHex())) {
            return ResponseBean.error("用户名或原密码不正确");
        }

        if(!form.getNewPassword().equals(form.getNewPasswordAgain())) {
            return ResponseBean.error("两次新密码不一致");
        }

        // 修改密码
        userDto.setPassword(new Sha256Hash(form.getNewPassword(), userDto.getSalt()).toHex());
        sysUserDao.updateById(userDto);

        // 删除短信验证码
        sysValidCodeDao.deleteById(codeDto.getUuid());
        return new ResponseBean(HttpStatus.OK.value(), "密码重置成功", null);
    }

    /**
     * 调查人员列表
     * @param coSurveyorDto
     * @return
     */
    @Override
    public List<CoSurveyorDto> findSurveyorList(CoSurveyorDto coSurveyorDto) {
        CoSurveyorDto surveyorDto = coSurveyorDao.querySurveyorByUserId(getUserId());
        coSurveyorDto.setCoId(surveyorDto.getCoId());
        PageHelper.startPage(coSurveyorDto.getPage(),coSurveyorDto.getRows(),"g.operatetimeforhis "+coSurveyorDto.getSord());
        return coSurveyorDao.findSurveyorList(coSurveyorDto);
    }

    /**
     * 前台导出查询翻译
     * @param coSurveyorDto
     * @return
     */
    @Override
    public List<CoSurveyorDto> findTranInfoQTList(CoSurveyorDto coSurveyorDto) {
        CoDto coDto = new CoDto();
        coDto.setUserId(getUserId());
        coDto = coDao.selectOne(coDto);
        coSurveyorDto.setCoId(coDto.getCoId());
        return coSurveyorDao.findTranInfoQTList(coSurveyorDto);
    }

    /**
     * 调查人员导入模板下载
     * @param request
     * @param response
     */
    @Override
    public void downCoSurveyorTpl(HttpServletRequest request, HttpServletResponse response) {
        List<JSONObject> rowNames = buildCoSurveyorTemplateRowNames();
        AssertUtil.checkNotNull(rowNames);
        try {
            ExcelUtil.downloadExcelTemplate(request, response, rowNames, "调查人员导入模板");
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /**
     * 构建调查人员导入模板Excel的表头信息
     * @return
     */
    private List<JSONObject> buildCoSurveyorTemplateRowNames() {
        List<JSONObject> rowNames = new ArrayList<>();
        JSONObject rowName;
        for(CoSurveyorRowName item : CoSurveyorRowName.values()) {
            rowName = new JSONObject();
            rowName.put("excelName", item.getExcelName());
            rowName.put("name", item.getName());
            rowName.put("need", item.getNeed());
            rowNames.add(rowName);
        }
        return rowNames;
    }

    /**
     * 批量数据导入
     * @param file
     * @param operatorId
     * @return
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public JSONObject batchImportCoSurveyorData(MultipartFile file, String operatorId) throws Exception {
        AssertUtil.checkNotNull(file);
        AssertUtil.checkNotNull(operatorId);
        // 获取Excel头信息
        List<JSONObject> rowNames = buildCoSurveyorTemplateRowNames();
        // 从Excel转化为JSON数据
        List<JSONObject> data = ExcelUtil.getExcelInfo(file, rowNames);
        data = data.stream().filter(d -> d.size() > 0).collect(Collectors.toList());
        boolean coSurveyorResult;
        int errorNum = 0;
        int totalNum = 0;
        JSONArray errorJA = new JSONArray();
        if(EmptyUtil.isNotEmpty(data)) {
            totalNum = data.size();
            for(JSONObject coSurveyorData : data) {
                try {
                    coSurveyorResult = handleExcelCoSurveyorData(operatorId, coSurveyorData);
                    if(!coSurveyorResult) {
                        coSurveyorData.put("errorMsg", "未知错误，请联系管理员");
                    }
                }catch (ExcelException e) {
                    coSurveyorResult = false;
                    coSurveyorData.put("errorMsg", e.getMessage());
                    errorJA.add(coSurveyorData);
                }
                if(!coSurveyorResult) {
                    errorNum ++;
                }
            }
        }else {
            throw new ExcelException(ResponseCode.IMPORT_DATE_ERROR);
        }

        JSONObject result = new JSONObject();
        result.put("totalNum", totalNum);
        result.put("totalErrorNum", errorNum);
        result.put("totalSuccessNum", totalNum - errorNum);
        // 构建错误清单列表信息
        if(EmptyUtil.isNotEmpty(errorJA)) {
            String errorCode = IdGen.uuid();
            ErrorListUtil.putCache(buildErrorListContent(errorJA), errorCode);
            result.put("errorCode", errorCode);
        }
        return result;
    }

    /**
     * 构建导入区域错误列表Excel数据格式
     * @param errorJA
     * @return
     */
    public JSONArray buildErrorListContent(JSONArray errorJA) {
        return ErrorListUtil.buildErrorListContent(errorJA, buildCoSurveyorTemplateRowNames());
    }

    /**
     * 处理调查人员数据导入
     * @param operatorId
     * @param coSurveyor
     * @return
     */
    public boolean handleExcelCoSurveyorData(String operatorId, JSONObject coSurveyor) throws Exception {
        String surveyorCode = coSurveyor.getString(CoSurveyorRowName.SURVEYOR_CODE.getName());
        String surveyorName = coSurveyor.getString(CoSurveyorRowName.SURVEYOR_NAME.getName());
        AssertUtil.checkNotNull(surveyorName, "调查人员姓名不能为空");
        String eeType = coSurveyor.getString(CoSurveyorRowName.EE_TYPE.getName());
        AssertUtil.checkNotNull(eeType, "员工身份不能为空");
        // 数据转换
        if(!(eeType != null && ( eeType.equals("系统管理员") || eeType.equals("普通员工") || eeType.equals("调查人员")))){
            throw  new ExcelException("员工身份录入错误,应该是系统管理员或者普通员工或者调查人员");
        }
        if (eeType != null && eeType.equals("系统管理员")) {
            eeType = Constant.SurveyorEeType.STAFFADMINISTRATOR.getCode();
        } else if (eeType != null && eeType.equals("普通员工" )) {
            eeType = Constant.SurveyorEeType.ORDINARYSTAFF.getCode();
        } else if (eeType != null && eeType.equals("调查人员")) {
            eeType = Constant.SurveyorEeType.SURVEYOR.getCode();
        }
        String userName = coSurveyor.getString(CoSurveyorRowName.USER_NAME.getName());
        AssertUtil.checkNotNull(eeType, "用户名不能为空");
        String mobile = coSurveyor.getString(CoSurveyorRowName.MOBILE.getName());
        AssertUtil.checkNotNull(mobile, "调查人员电话不能为空");
        String email = coSurveyor.getString(CoSurveyorRowName.EMAIL.getName());
        AssertUtil.checkNotNull(email, "电子邮箱不能为空");
        String validInd = coSurveyor.getString(CoSurveyorRowName.VALID_IND.getName());
        AssertUtil.checkNotNull(validInd, "有效状态不能为空");
        if(!(validInd != null && ( validInd.equals("有效") || validInd.equals("无效") ))){
            throw  new ExcelException("有效状态录入错误，应该是有效或无效,请检查");
        }
        if (validInd != null && validInd.equals("有效")) {
            validInd = "1";
        } else if (validInd != null && validInd.equals("无效" )) {
            validInd = "0";
        }
        String remark = coSurveyor.getString(CoSurveyorRowName.REMARK.getName());
        boolean result = true;
        int count = 0;
        // 以调查人员代码是否存在作为新增还是更新
        CoSurveyorDto coSurveyorDto = null;
        SysUserDto user = null;
        SysDictDataDto sysDictDataDto = null;
        if(EmptyUtil.isNotEmpty(surveyorCode)){
            coSurveyorDto = this.selectOne(new EntityWrapper<CoSurveyorDto>().eq("surveyor_code",surveyorCode));
            AssertUtil.checkNotNull(coSurveyorDto, "调查人员代码不存在,请检查");
            // 更新步骤

            // 设置角色
            sysDictDataDto = new SysDictDataDto();

            coSurveyorDto.setUserName(userName);
            // 更新用户信息
            user = new SysUserDto();
            user.setUserId(coSurveyorDto.getUserId());
            user = sysUserDao.selectOne(user);
            if(coSurveyorDto.getEeType().equals(Constant.SurveyorEeType.ORDINARYSTAFF.getCode())) {
                user.setUserType(Constant.userType.ORDINARYSTAFF.getCode());
                sysDictDataDto.setDictType(Constant.SysRole.ORDINARYSTAFFROLE.getCode());
            } else if (coSurveyorDto.getEeType().equals(Constant.SurveyorEeType.SURVEYOR.getCode())) {
                user.setUserType(Constant.userType.SURVEYOR.getCode());
                sysDictDataDto.setDictType(Constant.SysRole.SURVEYORROLE.getCode());
            } else if (coSurveyorDto.getEeType().equals(Constant.SurveyorEeType.STAFFADMINISTRATOR.getCode())) {
                user.setUserType(Constant.userType.COUSER.getCode());
                sysDictDataDto.setDictType(Constant.SysRole.COROLE.getCode());
            }
            user.setMobile(mobile);
            user.setValidInd(validInd);
            ToolsUtils.setUpdateUserAndDate(user);


            // 更新调查员信息
            coSurveyorDto.setSurveyorName(surveyorName);
            coSurveyorDto.setEeType(eeType);
            coSurveyorDto.setMobile(mobile);
            coSurveyorDto.setEmail(email);
            coSurveyorDto.setValidInd(validInd);
            coSurveyorDto.setRemark(remark);
            ToolsUtils.setUpdateUserAndDate(coSurveyorDto);

            // 校验用户手机号
            this.verifyMobileForExcel(coSurveyorDto, Constant.ValidType.UPDATE.getCode());
            // 校验用户名
            this.verifyUserNameForExcel(coSurveyorDto, Constant.ValidType.UPDATE.getCode());

            sysUserDao.updateById(user);
            count = coSurveyorDao.updateById(coSurveyorDto);

            // 更新角色
            sysDictDataDto = sysDictDataDao.selectOne(sysDictDataDto);
            SysUserRoleDto sysUserRoleDto = new SysUserRoleDto();
            sysUserRoleDto.setUserId(user.getUserId());
            sysUserRoleDto = sysUserRoleDao.selectOne(sysUserRoleDto);
            sysUserRoleDto.setRoleId(Long.valueOf(sysDictDataDto.getDictCode()));
            ToolsUtils.setUpdateUserAndDate(sysUserRoleDto);
            sysUserRoleDao.updateById(sysUserRoleDto);
        }else{
            // 新建步骤

            // 设置邮件内容
            String serverUrl = sysConfigService.getValue(Constant.SERVER_URL);
            String outLoginUrl = sysConfigService.getValue(Constant.OUT_LOGIN_URL);
            String mobileLoginUrl = sysConfigService.getValue(Constant.MOBILE_LOGIN_URL);
            StringBuilder mailConent = new StringBuilder();

            // 设置角色
            sysDictDataDto = new SysDictDataDto();

            // 新增用户信息
            user = new SysUserDto();
            user.setUserCode(userName);
            user.setUserName(userName);
            user.setMobile(mobile);
            user.setValidInd(validInd);


            //sha256加密
            String salt = RandomStringUtils.randomAlphanumeric(20);
            user.setPassword(new Sha256Hash(Constant.SURVEYOR_INIT_PWD, salt).toHex());
            user.setSalt(salt);
            ToolsUtils.setUserAndDate(user);


            // 保存调查员信息
            CoDto coDto = new CoDto();
            coDto.setUserId(getUserId());
            coDto = coDao.selectOne(coDto);
            coSurveyorDto = new CoSurveyorDto();
            coSurveyorDto.setCoId(coDto.getCoId());
            coSurveyorDto.setSurveyorCode(CodeGenerator.getInstance().generateSurveyorCode(coDto.getCoCode()));
            coSurveyorDto.setSurveyorName(surveyorName);
            coSurveyorDto.setEeType(eeType);
            coSurveyorDto.setMobile(mobile);
            coSurveyorDto.setEmail(email);
            coSurveyorDto.setOperateStatus(Constant.NORMAL_STATUS);
            coSurveyorDto.setValidInd(validInd);
            coSurveyorDto.setRemark(remark);
            coSurveyorDto.setUserName(userName);
            ToolsUtils.setUserAndDate(coSurveyorDto);
            // 校验用户手机号
            this.verifyMobileForExcel(coSurveyorDto, Constant.ValidType.ADD.getCode());
            // 校验用户名
            this.verifyUserNameForExcel(coSurveyorDto, Constant.ValidType.ADD.getCode());
            if(eeType.equals(Constant.SurveyorEeType.ORDINARYSTAFF.getCode())) {
                user.setUserType(Constant.userType.ORDINARYSTAFF.getCode());
                sysDictDataDto.setDictType(Constant.SysRole.ORDINARYSTAFFROLE.getCode());
                mailConent.append("你的注册账户登录用户").append(coSurveyorDto.getUserName()).append(",用户密码").append(Constant.SURVEYOR_INIT_PWD)
                        .append(",登录地址;").append(serverUrl).append("/").append(outLoginUrl);
            } else if (eeType.equals(Constant.SurveyorEeType.SURVEYOR.getCode())) {
                user.setUserType(Constant.userType.SURVEYOR.getCode());
                sysDictDataDto.setDictType(Constant.SysRole.SURVEYORROLE.getCode());
                mailConent.append("你的注册账户登录用户").append(coSurveyorDto.getUserName()).append(",用户密码").append(Constant.SURVEYOR_INIT_PWD)
                        .append(",登录地址;").append(serverUrl).append("/").append(mobileLoginUrl);
            } else if (coSurveyorDto.getEeType().equals(Constant.SurveyorEeType.STAFFADMINISTRATOR.getCode())) {
                user.setUserType(Constant.userType.COUSER.getCode());
                sysDictDataDto.setDictType(Constant.SysRole.COROLE.getCode());
                mailConent.append("你的注册账户登录用户").append(coSurveyorDto.getUserName()).append(",用户密码").append(Constant.SURVEYOR_INIT_PWD)
                        .append(",登录地址;").append(serverUrl).append("/").append(outLoginUrl);
            }
            sysUserDao.insert(user);
            coSurveyorDto.setUserId(user.getUserId());
            count = coSurveyorDao.insert(coSurveyorDto);

            // 给调查人员配置角色
            sysDictDataDto = sysDictDataDao.selectOne(sysDictDataDto);
            SysUserRoleDto sysUserRoleDto = new SysUserRoleDto();
            sysUserRoleDto.setUserId(user.getUserId());
            sysUserRoleDto.setRoleId(Long.valueOf(sysDictDataDto.getDictCode()));
            ToolsUtils.setUserAndDate(sysUserRoleDto);
            sysUserRoleDao.insert(sysUserRoleDto);


            // 发送邮件
            MailSendUtils.sendEmail(coSurveyorDto.getEmail(),"调查平台消息提醒", mailConent.toString(), null);

            // 短信通知
            SmsUtils.sendShortMessage(mailConent.toString(), coSurveyorDto.getMobile());
        }
        // 更新失败
        if(count <= 0){
            result = false;
        }
        return result;
    }

    /**
     * 校验调查员用户名(excel)
     * @param coSurveyorDto
     * @return
     */
    private void verifyUserNameForExcel(CoSurveyorDto coSurveyorDto, String type) throws Exception{
        SysUserDto user= new SysUserDto();
        user.setUserName(coSurveyorDto.getUserName());
        user = sysUserDao.selectOne(user);
        if(type.equals(Constant.ValidType.ADD.getCode())){
            if(user != null) {
                throw  new ExcelException("该用户名已被注册,请检查");
            }
        }else if(type.equals(Constant.ValidType.UPDATE.getCode())){
            // 更新如果导入的手机号用户表可以查到，且用户id与调查人员的用户id一致，则可以,否则报已被注册
            if(user != null){
                if( !user.getUserId().equals(coSurveyorDto.getUserId())){
                    throw  new ExcelException("该手机号已被注册,请检查");
                }
            }
        }
    }

    /**
     * 校验调查员手机号(excel)
     * @param coSurveyorDto
     * @return
     */
    private  void verifyMobileForExcel(CoSurveyorDto coSurveyorDto, String type) throws Exception{
        SysUserDto user= new SysUserDto();
        user.setMobile(coSurveyorDto.getMobile());
        user = sysUserDao.selectOne(user);
        if(type.equals(Constant.ValidType.ADD.getCode())){
            // 新增的判断逻辑，只需要判断有没有即可
            if(user != null) {
                throw  new ExcelException("该手机号已被注册,请检查");
            }
        }else if(type.equals(Constant.ValidType.UPDATE.getCode())){
            // 更新如果导入的手机号用户表可以查到，且用户id与调查人员的用户id一致，则可以,否则报已被注册
            if(user != null){
                if( !user.getUserId().equals(coSurveyorDto.getUserId())){
                    throw  new ExcelException("该手机号已被注册,请检查");
                }
            }
        }


    }

}