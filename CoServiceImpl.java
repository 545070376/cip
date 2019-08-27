/*
 * sinosoft https://github.com/wliduo
 * Created By sinosoft
 * Date By (2019-05-06 16:40:45)
 */
package io.piccsz.modules.cip.service.impl;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.baomidou.mybatisplus.mapper.EntityWrapper;
import com.baomidou.mybatisplus.service.impl.ServiceImpl;
import com.github.pagehelper.PageHelper;
import com.google.common.collect.Lists;
import io.piccsz.common.dto.ResponseBean;
import io.piccsz.common.exception.CustomException;
import io.piccsz.common.exception.ExcelException;
import io.piccsz.common.sms.SmsUtils;
import io.piccsz.common.utils.AssertUtil;
import io.piccsz.common.utils.Constant;
import io.piccsz.common.utils.EmptyUtil;
import io.piccsz.common.utils.ExcelUtil;
import io.piccsz.common.utils.IdGen;
import io.piccsz.common.utils.RedisUtils;
import io.piccsz.common.utils.ResponseCode;
import io.piccsz.common.utils.StringUtil;
import io.piccsz.common.utils.ToolsUtils;
import io.piccsz.modules.cip.dao.*;
import io.piccsz.modules.cip.dto.common.MachineRowName;
import io.piccsz.modules.cip.dto.custom.*;
import io.piccsz.modules.cip.form.CoRegisterForm;
import io.piccsz.modules.cip.service.IBusDimValService;
import io.piccsz.modules.cip.service.ICoService;
import io.piccsz.modules.cip.service.ITmplService;
import io.piccsz.modules.cip.utils.CodeGenerator;
import io.piccsz.modules.cip.utils.ErrorListUtil;
import io.piccsz.modules.sys.dao.SysDictDataDao;
import io.piccsz.modules.sys.dao.SysUserDao;
import io.piccsz.modules.sys.dao.SysUserRoleDao;
import io.piccsz.modules.sys.dao.SysValidCodeDao;
import io.piccsz.modules.sys.dto.custom.SysDictDataDto;
import io.piccsz.modules.sys.dto.custom.SysUserDto;
import io.piccsz.modules.sys.dto.custom.SysUserRoleDto;
import io.piccsz.modules.sys.dto.custom.SysValidCodeDto;
import io.piccsz.modules.sys.form.PasswordForm;
import io.piccsz.modules.sys.service.SysDictDataService;
import io.piccsz.modules.sys.service.SysUserService;
import org.apache.commons.lang.RandomStringUtils;
import org.apache.commons.lang.StringUtils;
import org.apache.shiro.crypto.hash.Sha256Hash;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.aop.framework.AopContext;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.tools.Tool;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

import static io.piccsz.common.utils.ShiroUtils.getUserId;

/**
 * CoServiceImpl
 * @author Generator
 * @date 2019-05-06 16:40:45
 */
@Service("coService")
public class CoServiceImpl extends ServiceImpl<CoDao, CoDto> implements ICoService {

    private static final Logger logger = LoggerFactory.getLogger(CoServiceImpl.class);

    @Autowired
    private CoDao coDao;

    @Autowired
    private SysUserDao sysUserDao;

    @Autowired
    private BusAuditDao busAuditDao;

    @Autowired
    private RedisUtils redisUtils;

    @Autowired
    private ITmplService tmplService;

    @Autowired
    private IBusDimValService busDimValService;

    @Autowired
    private DimDao dimDao;

    @Autowired
    private SysValidCodeDao sysValidCodeDao;

    @Autowired
    private SysUserRoleDao sysUserRoleDao;

    @Autowired
    private BusFileDao busFileDao;

    @Autowired
    private CoSurveyorDao coSurveyorDao;

    @Autowired
    private SysUserService sysUserService;

    @Autowired
    private SysDictDataDao sysDictDataDao;

    @Autowired
    private BusDimValDao busDimValDao;

    @Autowired
    private SysDictDataService sysDictDataService;

    private TmplDto TMPLDTO = new TmplDto();
    /**
     * 列表
     * @param coDto
     * @return java.util.List<io.piccsz.modules.cip.dto.custom.CoDto;>
     * @author Generator
     * @date 2019-05-06 16:40:45
     */
    @Override
    public List<CoDto> findPageInfo(CoDto coDto) {
        return coDao.findPageInfo(coDto);
    }

    /**
     * 机选机构匹配
     *
     * @param caseSolDto
     * @return java.util.List<io.piccsz.modules.cip.dto.custom.CoDto>
     * @throws
     * @author wliduo[i@dolyw.com]
     * @date 2019/8/21 10:49
     */
    @Override
    public List<CoDto> findPageCoInfo(CaseSolDto caseSolDto) {
        List<CoDto> coDtoList = new ArrayList<CoDto>();
        // 模板SQL
        final String SQL_TPL = "SELECT bus_id FROM ( SELECT bus_id, substring_index( substring_index( dim_opt_code, ';', h.help_topic_id + 1 ), ';' ,- 1 ) CODE " +
                "FROM t_cip_bus_dim_val JOIN help_topic h ON h.help_topic_id < ( length(dim_opt_code) - length( REPLACE (dim_opt_code, ';', '') ) + 1 ) ) t WHERE ( ";
        // 查询当前案件的机选规则
        List<BusDimValDto> busDimValDtoList = busDimValDao.selectList(new EntityWrapper<BusDimValDto>()
                .eq("bus_id", caseSolDto.getCaseId()).eq("bus_type", "2")
                .eq("operate_status", Constant.VALID_IND)
                .eq("valid_ind", Constant.VALID_IND));
        // 进行SQL拼写
        StringBuffer sql = new StringBuffer("SELECT bus_id FROM t_cip_bus_dim_val d WHERE d.bus_type = '1' AND bus_id IN (");
        // 统计有效数据
        int count = 0;
        for (int i = 0, len = busDimValDtoList.size(); i < len; i++) {
            BusDimValDto busDimValDto = busDimValDtoList.get(i);
            // 不为空判断
            if (StringUtils.isNotBlank(busDimValDto.getDimOptCode())) {
                sql.append(SQL_TPL);
                // 根据分号分割为数组
                String[] strs = busDimValDto.getDimOptCode().split(";");
                for (int j = 0; j < strs.length; j++) {
                    sql.append(" CODE = '" + strs[j] + "' ");
                    // 最后一个不加OR
                    if (j != strs.length - 1) {
                        sql.append(" OR ");
                    }
                }
                sql.append(") ");
                // 最后一个不加AND
                if (i != len - 1) {
                    sql.append(" AND bus_id IN ( ");
                }
                // 统计有效数据
                count++;
            }
        }
        // 有效数据结尾括号拼接
        for (int i = 0; i < count - 1; i++) {
            sql.append(" ) ");
        }
        // 最后一个结尾
        sql.append(" ) GROUP BY bus_id");
        logger.info("机选规则匹配查询SQL：{}", sql.toString());
        List<String> busIdList = new ArrayList<>();
        try {
            busIdList = busDimValDao.findBusIdList(sql.toString());
        } catch (Exception e) {
            logger.error("机选规则匹配查询失败，数据有误：{}", e.getMessage());
        }
        // 匹配到机选查询机选机构详细信息
        if (busIdList.size() > 0) {
            caseSolDto.setBusIdList(busIdList);
            PageHelper.startPage(caseSolDto.getPage(), caseSolDto.getRows(), "cc.asmt_lvl is null, cc.asmt_lvl asc" );
            coDtoList = coDao.findPageCoInfo(caseSolDto);
        }
        return coDtoList;
    }

    /**
     * 根据工号，查询机构用户或调查员
     * @param userCode
     * @return
     */
    @Override
    public SysUserDto queryUserByUserCode(String userCode) {
        SysUserDto userDto = sysUserDao.queryCoOrSurveyorByUserCode(userCode);
        return userDto;
    }

    /**
     * 校验机构用户名
     * @param form
     * @return
     */
    @Override
    public ResponseBean verifyUserName(CoRegisterForm form) {
        SysUserDto user= new SysUserDto();
        user.setUserName(form.getUserName());
        user = sysUserDao.selectOne(user);
        if(user != null) {
            if(user.getUserType().equals(Constant.userType.COUSER.getCode())) {
                CoDto coDto = new CoDto();
                coDto.setUserId(user.getUserId());
                coDto = coDao.selectOne(coDto);
                // 如果机构状态是待审核或审核通过，则不允许再注册
                if(coDto != null) {
                    if (coDto.getAuditStatus().equals(Constant.AuditStatus.TOBEAUDIT.getCode()) ||
                            coDto.getAuditStatus().equals(Constant.AuditStatus.AUDITASS.getCode())) {
                        return ResponseBean.error("该用户名已被注册");
                    }
                } else {
                    return ResponseBean.error("该用户名已被注册");
                }
            } else {
                return ResponseBean.error("该用户名已被注册");
            }
        }
        return new ResponseBean(HttpStatus.OK.value(), "校验成功", null);
    }

    /**
     * 校验机构手机号
     * @param form
     * @return
     */
    @Override
    public ResponseBean verifyMobile(CoRegisterForm form) {
        SysUserDto user= new SysUserDto();
        user.setMobile(form.getMobile());
        user = sysUserDao.selectOne(user);
        if(user != null) {
            if(user.getUserType().equals(Constant.userType.COUSER.getCode())) {
                CoDto coDto = new CoDto();
                coDto.setUserId(user.getUserId());
                coDto = coDao.selectOne(coDto);
                // 如果机构状态是待审核或审核通过，则不允许再注册
                if(coDto != null){
                    if(coDto.getAuditStatus().equals(Constant.AuditStatus.TOBEAUDIT.getCode())||
                            coDto.getAuditStatus().equals(Constant.AuditStatus.AUDITASS.getCode())) {
                        return ResponseBean.error("该手机号已被注册");
                    }
                }else{
                    return ResponseBean.error("该手机号已被注册");
                }
            } else {
                return ResponseBean.error("该手机号已被注册");
            }

        }
        return new ResponseBean(HttpStatus.OK.value(), "校验成功", null);
    }

    /**
     * 机构注册
     * @param form
     * @return
     * @throws Exception
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public ResponseBean register(CoRegisterForm form) throws Exception {
        Date now = new Date();
        SysValidCodeDto codeDto = new SysValidCodeDto();
        codeDto.setMobile(form.getMobile());
        codeDto.setCode(form.getValidCode());
        codeDto = sysValidCodeDao.selectOne(codeDto);
        if (codeDto == null) {
            return ResponseBean.error("短信验证码不正确");
        }

        if(!form.getPassword().equals(form.getConfirmPassword())) {
            return ResponseBean.error("原密码和确认密码不一致");
        }

        // 校验用户名，如果不存在此用户，则新增用户，如果存在此用户并且是审核驳回，则更新用户
        SysUserDto user = new SysUserDto();
        CoDto co = new CoDto();
        Long userId = new Long(0);
        SysUserDto sysUserDto =  new SysUserDto();
        sysUserDto.setUserName(form.getUserName());
        sysUserDto = sysUserDao.selectOne(sysUserDto);
        SysDictDataDto sysDictDataDto = new SysDictDataDto();
        sysDictDataDto.setDictType(Constant.DEFAULT_ADMINISTRATOR);
        sysDictDataDto = sysDictDataDao.selectOne(sysDictDataDto);
        if(sysUserDto != null) {
            CoDto coDto = new CoDto();
            coDto.setUserId(sysUserDto.getUserId());
            coDto = coDao.selectOne(coDto);
            // 如果机构状态是待审核或审核通过，则不允许再注册
            if(coDto.getAuditStatus().equals(Constant.AuditStatus.TOBEAUDIT.getCode())||
                    coDto.getAuditStatus().equals(Constant.AuditStatus.AUDITASS.getCode())) {
                return ResponseBean.error("该用户名已被注册");
            } else {
                // 更新用户信息
                BeanUtils.copyProperties(form, user);
                user.setUserId(coDto.getUserId());
                user.setUserCode(form.getUserName());
                user.setUserType(Constant.userType.COUSER.getCode());
                //sha256加密
                String salt = RandomStringUtils.randomAlphanumeric(20);
                user.setPassword(new Sha256Hash(form.getPassword(), salt).toHex());
                user.setSalt(salt);
                user.setOperateStatus(Constant.NORMAL_STATUS);
                user.setValidInd(Constant.UN_VALID_IND);
                user.setUpdatercode(String.valueOf(user.getUserId()));
                user.setOperatetimeforhis(now);
                sysUserDao.updateById(user);
                userId = user.getUserId();

                // 更新机构信息
                BeanUtils.copyProperties(form, co);
                co.setCoId(coDto.getCoId());
                co.setUserId(userId);
                co.setAuditStatus(Constant.CO_AUDIT_WAIT);
                co.setUpdatercode(String.valueOf(userId));
                co.setOperatetimeforhis(now);
                coDao.updateById(co);
            }
        } else {
            // 保存用户信息
            BeanUtils.copyProperties(form, user);
            user.setUserCode(form.getUserName());
            user.setUserType(Constant.userType.COUSER.getCode());
            //sha256加密
            String salt = RandomStringUtils.randomAlphanumeric(20);
            user.setPassword(new Sha256Hash(form.getPassword(), salt).toHex());
            user.setSalt(salt);
            user.setOperateStatus(Constant.NORMAL_STATUS);
            user.setValidInd(Constant.UN_VALID_IND);
            user.setInserttimeforhis(now);
            user.setCreatorcode(String.valueOf(sysDictDataDto.getDictCode()));
            user.setOperatetimeforhis(now);
            user.setUpdatercode(String.valueOf(sysDictDataDto.getDictCode()));
            sysUserDao.insert(user);
            userId = user.getUserId();

            // 保存机构信息
            BeanUtils.copyProperties(form, co);
            co.setCoCode("DC"+ getCoCode());
            co.setCoType(Constant.CoType.REGISTER.getCode());
            co.setUserId(userId);
            co.setCoUuid(UUID.randomUUID().toString().replace("-", ""));
            co.setAuditStatus(Constant.CO_AUDIT_WAIT);
            co.setCreatorcode(String.valueOf(userId));
            co.setInserttimeforhis(now);
            co.setUpdatercode(String.valueOf(userId));
            co.setOperatetimeforhis(now);
            coDao.insert(co);

        }

        // 将案件信息相关文件逻辑删除
        BusFileDto fileDto = new BusFileDto();
        fileDto.setBusType(Constant.FileBusType.REGISTCO.getCode());
        fileDto.setBusId(co.getCoId());
        fileDto.setOperateStatus(Constant.NORMAL_STATUS);
        fileDto.setValidInd(Constant.NORMAL_STATUS);
        List<BusFileDto> fileList = busFileDao.findPageInfo(fileDto);
        for (BusFileDto busFileDto : fileList) {
            busFileDto.setOperateStatus(Constant.DELETE_STATUS);
            busFileDto.setValidInd(Constant.DELETE_STATUS);
            this.busFileDao.updateById(busFileDto);
        }

        // 新增案件信息文件
        List<BusFileDto> busFileDtoList = form.getBusFileDtoList();
        String userIdStr = String.valueOf(userId);
        if (busFileDtoList != null && busFileDtoList.size() > 0) {
            for (BusFileDto busFileDto : busFileDtoList) {
                busFileDto.setBusId(co.getCoId());
                busFileDto.setBusType(Constant.FileBusType.REGISTCO.getCode());
                busFileDto.setFileType(Constant.FileFileType.ELSE.getCode());
                busFileDto.setCreatorcode(userIdStr);
                busFileDto.setUpdatercode(userIdStr);
                busFileDto.setInserttimeforhis(now);
                busFileDto.setCreatorcode(String.valueOf(userId));
                busFileDto.setOperatetimeforhis(now);
                busFileDto.setCreatorcode(String.valueOf(userId));
                busFileDto.setOperateStatus(Constant.NORMAL_STATUS);
                busFileDto.setValidInd(Constant.VALID_IND);
                this.busFileDao.insert(busFileDto);
            }
        }

        // 添加流程审核信息(提交人审核数据)
        SysDictDataDto dictDataDto = new SysDictDataDto();
        dictDataDto.setDictType(Constant.SysRole.COROLE.getCode());
        dictDataDto = sysDictDataDao.selectOne(dictDataDto);
        BusAuditDto audit = new BusAuditDto();
        audit.setBusType(Constant.BusType.REGISTERCO.getCode());
        audit.setBusId(co.getCoId());
        audit.setNodeName(dictDataDto.getDictCode());
        audit.setBusStatus(Constant.BUS_STATUS_PENDING);
        BusAuditDto existOldAudit = busAuditDao.selectOne(audit);
        if (ToolsUtils.isEmpty(existOldAudit)) {
            audit.setFlowInTime(now);
            audit.setHandleTime(now);
            audit.setSubmitCode(userId);
            audit.setOperateCode(userId);
            audit.setBusStatus(Constant.BUS_STATUS_COMPLETED);
            audit.setAuditNo(UUID.randomUUID().toString().replace("-", "").toLowerCase());
            //给入库数据加入创建时间与更新时间
            audit.setCreatorcode(String.valueOf(userId));
            audit.setInserttimeforhis(now);
            audit.setUpdatercode(String.valueOf(userId));
            audit.setOperatetimeforhis(now);
            audit.setNodeName(Constant.NodeName.OUTSIDEUSER.getCode());
            busAuditDao.insert(audit);
        }else{
            audit.setHandleTime(now);
            audit.setCbaId(existOldAudit.getCbaId());
            audit.setBusStatus(Constant.BUS_STATUS_COMPLETED);
            busAuditDao.updateById(audit);
            //在更新后才赋值，避免更新不必要更新的数据
            audit.setAuditNo(existOldAudit.getAuditNo());
            //给入库数据加入创建时间与更新时间
            audit.setCreatorcode(String.valueOf(userId));
            audit.setInserttimeforhis(now);
            audit.setUpdatercode(String.valueOf(userId));
            audit.setOperatetimeforhis(now);
        }

        // 添加流程审核信息(机构审核岗审核数据)
        audit.setCbaId(null);
        //注册机构不用指定接下来的审核人员
        audit.setNodeName(null);
        audit.setSubmitCode(userId);
        audit.setOperateCode(null);
        audit.setAuditStatus(Constant.CO_AUDIT_WAIT);
        audit.setBusStatus(Constant.BUS_STATUS_PENDING);
        audit.setHandleTime(null);
        audit.setFlowInTime(now);
        busAuditDao.insert(audit);
        // 删除验证码
        sysValidCodeDao.deleteById(codeDto.getUuid());
        return new ResponseBean(HttpStatus.OK.value(), "注册成功", null);
    }

    /**
     * 查询机构注册信息
     * @param coUuid
     * @return
     */
    @Override
    public ResponseBean queryRegisterInfo(String coUuid) {
        CoRegisterForm registerForm = coDao.queryRegisterInfo(coUuid);
        if(registerForm != null) {
            BusFileDto busFileDto = new BusFileDto();
            busFileDto.setBusId(registerForm.getCoId());
            busFileDto.setBusType(Constant.FileBusType.REGISTCO.getCode());
            busFileDto.setOperateStatus(Constant.NORMAL_STATUS);
            busFileDto.setValidInd(Constant.VALID_IND);
            List<BusFileDto> busFileDtoList = busFileDao.queryBusFileList(busFileDto);
            registerForm.setBusFileDtoList(busFileDtoList);
        }
        return new ResponseBean(HttpStatus.OK.value(), "查询成功", registerForm);
    }

    /**
     * 修改密码
     * @param form
     */
    @Override
    public ResponseBean updatePassword(PasswordForm form) {

        return new ResponseBean(HttpStatus.OK.value(), "密码修改成功", null);
    }

    @Override
    public List<CoDto> getCoInfo(CoDto coDto) {
        return coDao.getCoInfo(coDto);
    }

    @Override
    public List<CoDto> getMacinList(CoDto coDto) {
        List<CoDto> coList = coDao.getMachList(coDto);
        TmplDto tmplDto = tmplService.selectByTmplId(coDto.getTmplId());
        List<TmplDimDto> tmplDimDtoList = tmplDto.getTmplDimList();
        if(ToolsUtils.isEmpty(tmplDimDtoList)){
            throw null;
        }
        Map map = new HashMap();
        TmplDimDto tmplDimDto = null;
        for(int i=0,len=tmplDimDtoList.size(); i < len ; i++){
            tmplDimDto = tmplDimDtoList.get(i);
            map.put(tmplDimDto.getDimCode(),tmplDimDto.getSortNo());
        }
        CoDto coTemp = null;
        for(int j=0,len=coList.size(); j < len ; j++){
            coTemp = coList.get(j);
            // 动态列名字段
            String dynamicColumn = coTemp.getDynamicColumn();
            // 动态列值
            String dynamicValue = coTemp.getDynamicValue();
            // 逻辑处理对应的动态字段字段赋值问题
            if(ToolsUtils.notEmpty(dynamicColumn)){
                String[] dynamicColumnStr = dynamicColumn.split(",");
                String[] dynamicValueStr =  dynamicValue.split(",");
                for(int k =0,lenA=dynamicColumnStr.length; k < lenA ; k++){
                    if(ToolsUtils.notEmpty(map.get(dynamicColumnStr[k]))){
                        if("1".equals(map.get(dynamicColumnStr[k]).toString())){
                            coTemp.setFieldAA(dynamicValueStr[k]);
                        }else if("2".equals(map.get(dynamicColumnStr[k]).toString())){
                            coTemp.setFieldAB(dynamicValueStr[k]);
                        }else if("3".equals(map.get(dynamicColumnStr[k]).toString())){
                            coTemp.setFieldAC(dynamicValueStr[k]);
                        }else if("4".equals(map.get(dynamicColumnStr[k]).toString())){
                            coTemp.setFieldAD(dynamicValueStr[k]);
                        }else if("5".equals(map.get(dynamicColumnStr[k]).toString())){
                            coTemp.setFieldAE(dynamicValueStr[k]);
                        }
                    }
                }
            }
        }
        return coList;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void insertMacinCo(CoDto coDto) throws Exception {
        CoDto existCo = this.selectOne(new EntityWrapper<CoDto>().eq("co_name", coDto.getCoName()));
        if (ToolsUtils.notEmpty(existCo)) {
            throw new CustomException("该机构名称已存在！请重新输入。");
        }
        if (ToolsUtils.notEmpty(sysUserService.selectOne(new EntityWrapper<SysUserDto>().eq("mobile", coDto.getPicMobile())))) {
            throw new CustomException("该手机号已存在！请重新输入。");
        }
        if (ToolsUtils.notEmpty(sysUserService.selectOne(new EntityWrapper<SysUserDto>().eq("user_code", coDto.getUserName())))) {
            throw new CustomException("该用户名已存在！请重新输入。");
        }

        //List<TmplDimDto> tmplDimDtoList = tmplService.getTmplDimList();
        coDto.setCoType(Constant.CO_TYPE_MACHINE);
        coDto.setOperateStatus(Constant.NORMAL_STATUS);
        coDto.setValidInd(Constant.VALID_IND);
        coDto = insertCoUser(coDto);

        List<BusDimValDto> busDimValDtos = coDto.getBusDimValList();
        for (BusDimValDto dimValDto : busDimValDtos) {
            if (StringUtils.isNotBlank(dimValDto.getDimVal())) {
                ToolsUtils.setUserAndDate(dimValDto);
                dimValDto.setBusId(coDto.getCoId());
                dimValDto.setBusType(Constant.DimValBusType.MACHINECO.getCode());
                dimValDto.setOperateStatus(Constant.NORMAL_STATUS);
                dimValDto.setValidInd(Constant.VALID_IND);
                dimValDto.setBusType(Constant.DIM_VAL_TYPE_MACHINE);
                busDimValService.insert(dimValDto);
            }
        }
        StringBuffer content = new StringBuffer("您的调查平台申请已通过，用户名为：");
        content.append(coDto.getUserName()).append("，密码为：picc@95518，现可登陆该平台接收我司指派任务");
        //SmsUtils.sendShortMessage(content.toString(), coDto.getPicMobile());
    }

    @Override
    public void downloadMacinImportTemplate(HttpServletRequest request, HttpServletResponse response,String tmplId) throws Exception {
        List<JSONObject> rowNames = buildMacinTemplateRowNames(tmplId);
        AssertUtil.checkNotNull(rowNames);
        try {
            ExcelUtil.downloadExcelTemplate(request, response, rowNames, "机选机构导入模板");
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public JSONObject batchImportMacin(MultipartFile file,String tmplId) throws Exception {
        AssertUtil.checkNotNull(file);
        // 获取Excel头信息
        List<JSONObject> rowNames = buildMacinTemplateRowNames(tmplId);
        // 从Excel转化为JSON数据
        List<JSONObject> data = ExcelUtil.getExcelInfo(file, rowNames);
        data = data.stream().filter(d -> d.size() > 0).collect(Collectors.toList());
        boolean machineResult;
        int errorNum = 0;
        int totalNum = 0;
        JSONArray errorJA = new JSONArray();
        if (EmptyUtil.isNotEmpty(data)) {
            totalNum = data.size();
            for (JSONObject machineData : data) {
                try {
                    machineResult = handleExcelMachinData(machineData, rowNames);
                    if (!machineResult) {
                        machineData.put("errorMsg", "未知错误，请联系管理员");
                    }
                } catch (ExcelException e) {
                    machineResult = false;
                    if (Constant.VALID_IND.equals(machineData.getString("validInd"))) {
                        machineData.put("validInd", Constant.VALID_CHINESE);
                    } else {
                        machineData.put("validInd", Constant.UN_VALID_CHINESE);
                    }
                    machineData.put("errorMsg", e.getMessage());
                    errorJA.add(machineData);
                }
                if (!machineResult) {
                    errorNum++;
                }
            }
        } else {
            throw new ExcelException(ResponseCode.IMPORT_DATE_ERROR);
        }

        JSONObject result = new JSONObject();
        result.put("totalNum", totalNum);
        result.put("totalErrorNum", errorNum);
        result.put("totalSuccessNum", totalNum - errorNum);
        // 构建错误清单列表信息
        if(EmptyUtil.isNotEmpty(errorJA)) {
            String errorCode = IdGen.uuid();
            ErrorListUtil.putCache(buildErrorListContent(errorJA,tmplId), errorCode);//buildErrorListContent(errorJA)
            result.put("errorCode", errorCode);
        }
        return result;
    }

    /**
     * 构建导入机选机构错误列表Excel数据格式
     * @param errorJA
     * @return
     */
    public JSONArray buildErrorListContent(JSONArray errorJA,String tmplId) {
        return ErrorListUtil.buildErrorListContent(errorJA, buildMacinTemplateRowNames(tmplId));
    }

    /**
     * 处理导入的机选机构数据
      * @Title CoServiceImpl
      * @Description
      * @author xruichang
      * @date 2019年05月18日 13:38
      * @version V1.0
      * @param machineData 机选机构data
      * @param rowNames 机选机构excel列名称数据
      * @return boolean
      * @throws
      */
    @Transactional(rollbackFor = Exception.class)
    public boolean handleExcelMachinData(JSONObject machineData, List<JSONObject> rowNames) throws Exception {
        for (JSONObject rowName : rowNames) {
            if (1 == rowName.getInteger(Constant.ROW_NEED)) {
                AssertUtil.checkNotNull(machineData.getString(rowName.getString(Constant.ROW_NAME)), rowName.getString(Constant.ROW_EXCEL_NAME) + "不能为空");
            }
        }
        if (Constant.VALID_CHINESE.equals(machineData.getString("validInd"))) {
            machineData.put("validInd", "1");
        } else {
            machineData.put("validInd", "0");
        }
        //获取动态字段的值
        List<BusDimValDto> busDimValList = checkDimVal(machineData);

        CoDto coDto = JSON.parseObject(JSON.toJSONString(machineData), CoDto.class);
        if (StringUtils.isEmpty(coDto.getCoCode())) {
            coDto.setCoCode("DC" + getCoCode());
            ToolsUtils.setUserAndDate(coDto);
            coDto.setOperateStatus(Constant.NORMAL_STATUS);
            coDto.setCoType(Constant.CO_TYPE_MACHINE);
            coDto.setAuditStatus(Constant.CO_AUDIT_WAIT);

            coDto = insertCoUser(coDto);

            for (BusDimValDto busDimValDto : busDimValList) {
                busDimValDto.setBusId(coDto.getCoId());
                ToolsUtils.setUserAndDate(busDimValDto);
            }

            busDimValService.insertBatch(busDimValList);

            StringBuffer content = new StringBuffer("您的调查平台申请已通过，用户名为：");
            content.append(coDto.getUserName()).append("，密码为：").append(Constant.MACHINUSERPASSWORD).append("，现可登陆该平台接收我司指派任务");
            SmsUtils.sendShortMessage(content.toString(), coDto.getPicMobile());
        } else {
            coDto = updatedCoUser(coDto);

            List<BusDimValDto> busDimVals = busDimValService.selectList(new EntityWrapper<BusDimValDto>()
                    .eq("bus_id",coDto.getCoId())
                    .eq("bus_type",Constant.DimValBusType.MACHINECO.getCode())
                    .eq("operate_status",Constant.NORMAL_STATUS)
                    .eq("valid_ind",Constant.VALID_IND));
            if (ToolsUtils.isEmpty(busDimVals)) {
                busDimValService.insertBatch(busDimValList);
            } else {
                String creatorCode = busDimVals.get(0).getCreatorcode();
                Date inserttimeforhis = busDimVals.get(0).getInserttimeforhis();
                //旧数据设置为无效
                for (BusDimValDto busDimVal : busDimVals) {
                    busDimVal.setValidInd(Constant.UN_VALID_IND);
                    busDimVal.setOperateStatus(Constant.DELETE_STATUS);
                    ToolsUtils.setUpdateUserAndDate(busDimVal);
                }
                busDimValService.updateBatchById(busDimVals);
                //插入新数据
                for (BusDimValDto busDimValDto : busDimValList) {
                    busDimValDto.setBusId(coDto.getCoId());
                    //保留历史创建人与创建时间
                    busDimValDto.setCreatorcode(creatorCode);
                    busDimValDto.setInserttimeforhis(inserttimeforhis);
                    //当前操作的登录用户
                    ToolsUtils.setUpdateUserAndDate(busDimValDto);
                }
                busDimValService.insertBatch(busDimValList);
            }
        }
        return true;
    }

    /**
     * 校验动态字段的数据的取值与校验值是否有问题
     * @Title CoServiceImpl
     * @Description
     * @author xruichang
     * @date 2019年05月31日 17:12
     * @version V1.0
     * @param machineData
      * @param busId
     * @return void
     * @throws
     */
    private List<BusDimValDto> checkDimVal(JSONObject machineData) throws Exception {
        List<BusDimValDto> busDimValList = Lists.newArrayList();
        for (String fieldName : machineData.keySet()) {
            if (fieldName.contains("field")) {
                int i = (int) fieldName.charAt(fieldName.length() - 1) - 64;
                DimDto tmplDimDto = TMPLDTO.getTmplDimList().get(i - 1).getDimDto();
                //枚举
                BusDimValDto dimValDto = new BusDimValDto();
                dimValDto.setBusType(Constant.DimValBusType.MACHINECO.getCode());
                dimValDto.setTmplId(TMPLDTO.getTmplId());
                String noExistValue = "";
                if (Constant.FieldType.ENUM.getCode().equals(tmplDimDto.getFieldType())) {
                    dimValDto.setDimCode(tmplDimDto.getDimOptDtoList().get(0).getDimCode());
                    String dimValueRelace = ((String) machineData.get(fieldName)).replace("；", ";");
                    String[] dimValues = dimValueRelace.split(";");
                    List<DimOptDto> dimOptList =  tmplDimDto.getDimOptDtoList();
                    StringBuffer dimOptNames = new StringBuffer();
                    StringBuffer noExistValues = new StringBuffer();
                    List<DimOptDto> insertDimList = new ArrayList<DimOptDto>();
                    //遍历对比多选模板内容
                    for (int j = 0; j < dimValues.length; j++) {
                        String dimValue = dimValues[j];
                        dimOptList.forEach(dimOptDto -> {
                                    if (dimValue.equals(dimOptDto.getDimOptName())) {
                                        dimOptNames.append(dimValue).append(";");
                                    }
                                }
                        );
                        //对比完成后，匹配的值与当前遍历到的下标j不一致说明没有匹配到dimOptName
                        if (dimOptNames.toString().split(";").length < j+1) {
                            noExistValues.append("“").append(dimValue).append("”").append("、");
                        }
                    }
                    if(ToolsUtils.notEmpty(noExistValues.toString())){
                        noExistValue = noExistValues.toString().substring(0, noExistValues.length() - 1);
                        throw new ExcelException(noExistValue + "；数据不存在，请确认。");
                    } else {
                        dimValDto.setDimVal(dimValueRelace);
                    }
                } else if (Constant.FieldType.ENUMS.getCode().equals(tmplDimDto.getFieldType())) {
                    String[] fieldValues = String.valueOf(machineData.get(fieldName)).split("-");
                    //多枚举数据若没有使用"-"分割
                    if (!(fieldValues.length == 2)) {
                        throw new ExcelException("“" + machineData.get(fieldName) + "”；数据存在问题，请确认,如是多枚举数据请按使用\"-\"进行分割");
                    }
                    StringBuffer dimCode = new StringBuffer();
                    for (int x = 0, y = fieldValues.length; x < y; x++) {
                        getcode(fieldValues[x], tmplDimDto.getDimOptDtoList(), dimCode);
                    }
                    //翻译出来的数据分割后长度若不等于2说明表格中传过来的数据有一个翻译不出来
                    if (!(dimCode.toString().split("-").length == 2)) {
                        throw new ExcelException("“" + machineData.get(fieldName) + "”；数据不存在，请确认。");
                    }
                    dimValDto.setDimCode(tmplDimDto.getDimOptDtoList().get(0).getDimCode());
                    dimValDto.setDimVal((String) machineData.get(fieldName));
                } else {
                    dimValDto.setDimCode((String) machineData.get(fieldName));
                    dimValDto.setDimVal((String) machineData.get(fieldName));
                }
                if (ToolsUtils.isEmpty(dimValDto.getDimCode())) {
                    throw new ExcelException("“" + machineData.get(fieldName) + "”；数据不存在，请确认。");
                }
                if (ToolsUtils.notEmpty(noExistValue)) {
                    throw new ExcelException("“" + noExistValue + "”，数据不存在，请确认。");
                }
                busDimValList.add(dimValDto);
            }
        }
        return busDimValList;
    }

    /**
     * 遍历对比excel中的名称与维度选项代码的名称查找出对应的code插入数据库
      * @Title CoServiceImpl
      * @Description
      * @author xruichang
      * @date 2019年05月31日 17:12
      * @version V1.0
      * @param fieldValue
      * @param DimOptList
      * @param dimVal
      * @return void
      * @throws
      */
    private void getcode(String fieldValue,List<DimOptDto> DimOptList,StringBuffer dimVal){
        if (ToolsUtils.isEmpty(DimOptList)) {
            return;
        }
        boolean hasCode = false;
        //筛选选项代码list是否查询到对应翻译
        for (DimOptDto dimOptDto : DimOptList) {
            if (!hasCode && fieldValue.equals(dimOptDto.getDimOptName())) {
                dimVal.append(dimOptDto.getDimOptCode()).append("-");
                hasCode = true;
            }
        }
        if (!hasCode) {
            //DimOptList查询不到翻译则回调查询子选项childDimOptList
            for (DimOptDto dimOptDto : DimOptList) {
                getcode(fieldValue, dimOptDto.getChildDimOptList(), dimVal);
            }
        }
    }

    public CoDto insertCoUser(CoDto coDto) throws Exception {
        CoDto existCo = this.selectOne(new EntityWrapper<CoDto>().eq("co_name", coDto.getCoName()));
        if (ToolsUtils.notEmpty(existCo)) {
            throw new ExcelException("该机构名称已存在！");
        }
        if (ToolsUtils.notEmpty(sysUserService.selectOne(new EntityWrapper<SysUserDto>().eq("mobile", coDto.getPicMobile())))) {
            throw new ExcelException("该手机号已存在！");
        } else {
            if (coDto.getPicMobile().length() != 11) {
                throw new ExcelException("该手机号长度不正确！");
            } else if (!(ToolsUtils.checkMobileFormat(coDto.getPicMobile()))) {
                throw new ExcelException("请检查手机号是否正确");
            }
        }
        if (ToolsUtils.notEmpty(sysUserService.selectOne(new EntityWrapper<SysUserDto>().eq("user_code", coDto.getUserName())))) {
            throw new ExcelException("该用户名已存在！请重新输入。");
        }
        //导入时coAddr会有值
        if (ToolsUtils.notEmpty(coDto.getCoAddr())) {
            String[] addrs = coDto.getCoAddr().replace("，", ",").split(",");
            if (ToolsUtils.notEmpty(addrs[0])) {
                SysDictDataDto sysDictDataDto = sysDictDataService.selectOne(new EntityWrapper<SysDictDataDto>()
                        .eq("dict_type", Constant.SYS_DICT_TYPE_PROVINCE)
                        .eq("dict_value", addrs[0]));
                if (ToolsUtils.notEmpty(sysDictDataDto)) {
                    coDto.setCoAddrP(sysDictDataDto.getDictCode());
                } else {
                    throw new ExcelException("该公司省份地址不存在！请重新输入。");
                }
            } else {
                coDao.setAddrPNUll(coDto.getCoId());
            }
            if (ToolsUtils.notEmpty(addrs[1])) {
                SysDictDataDto sysDictDataDto = sysDictDataService.selectOne(new EntityWrapper<SysDictDataDto>()
                        .eq("dict_type", Constant.SYS_DICT_TYPE_CITY)
                        .eq("dict_value", addrs[1]));
                if (ToolsUtils.notEmpty(sysDictDataDto)) {
                    coDto.setCoAddrC(sysDictDataDto.getDictCode());
                } else {
                    throw new ExcelException("该公司城市地址不存在！请重新输入。");
                }
            } else {
                coDao.setAddrCNUll(coDto.getCoId());
            }
        }

        SysUserDto sysUserDto = new SysUserDto();
        sysUserDto.setUserName(coDto.getUserName());
        sysUserDto.setUserCode(coDto.getUserName());
        sysUserDto.setUserType(Constant.userType.COUSER.getCode());
        ToolsUtils.setUserAndDate(sysUserDto);
        // sha256加密
        String salt = RandomStringUtils.randomAlphanumeric(20);
        sysUserDto.setPassword(new Sha256Hash(Constant.MACHINUSERPASSWORD, salt).toHex());
        sysUserDto.setSalt(salt);
        sysUserDto.setMobile(coDto.getPicMobile());
        sysUserDao.insert(sysUserDto);
        coDto.setUserId(sysUserDto.getUserId());
        coDto.setCoUuid(UUID.randomUUID().toString().replace("-", ""));
        coDao.insert(coDto);

        // 配置员工管理员身份
        CoSurveyorDto surveyorDto = new CoSurveyorDto();
        surveyorDto.setCoId(coDto.getCoId());
        surveyorDto.setUserId(sysUserDto.getUserId());
        surveyorDto.setSurveyorCode(CodeGenerator.getInstance().generateSurveyorCode(coDto.getCoCode()));
        surveyorDto.setSurveyorName(coDto.getCoPic());
        surveyorDto.setEeType(Constant.SurveyorEeType.STAFFADMINISTRATOR.getCode());
        surveyorDto.setMobile(sysUserDto.getMobile());
        ToolsUtils.setUserAndDate(surveyorDto);
        coSurveyorDao.insert(surveyorDto);

        // 配置角色
        SysDictDataDto sysDictDataDto = new SysDictDataDto();
        sysDictDataDto.setDictType(Constant.SysRole.COROLE.getCode());
        sysDictDataDto = sysDictDataDao.selectOne(sysDictDataDto);
        SysUserRoleDto sysUserRoleDto = new SysUserRoleDto();
        sysUserRoleDto.setUserId(sysUserDto.getUserId());
        sysUserRoleDto.setRoleId(Long.valueOf(sysDictDataDto.getDictCode()));
        ToolsUtils.setUserAndDate(sysUserRoleDto);
        sysUserRoleDao.insert(sysUserRoleDto);
        return coDto;
    }

    private CoDto updatedCoUser(CoDto coDto) throws Exception {
        CoDto existCo = new CoDto();
        existCo.setCoCode(coDto.getCoCode());
        existCo = coDao.selectOne(existCo);
        if (ToolsUtils.isEmpty(existCo)) {
            throw new ExcelException("机构代码：" + coDto.getCoCode() + "不存在，请确认。");
        }
        coDto.setCoId(existCo.getCoId());


        SysUserDto sysUserDto = sysUserService.selectOne(new EntityWrapper<SysUserDto>().eq("user_code", coDto.getUserName()));

        if (ToolsUtils.notEmpty(sysUserDto) && !(sysUserDto.getUserId().equals(existCo.getUserId()))) {
            throw new ExcelException("用户：" + coDto.getUserName() + "重复，请确认。");
        }
        if (ToolsUtils.isEmpty(sysUserDto)) {
            throw new ExcelException("用户名不允许修改，请确认。");
        }

        if (ToolsUtils.notEmpty(coDto.getCoAddr())) {
            String[] addrs = coDto.getCoAddr().split("-");
            if (ToolsUtils.notEmpty(addrs[0])) {
                SysDictDataDto sysDictDataDto = sysDictDataService.selectOne(new EntityWrapper<SysDictDataDto>()
                        .eq("dict_type", Constant.SYS_DICT_TYPE_PROVINCE)
                        .eq("dict_value", addrs[0]));
                if (ToolsUtils.notEmpty(sysDictDataDto)) {
                    coDto.setCoAddrP(sysDictDataDto.getDictCode());
                } else {
                    throw new ExcelException("该公司省份地址不存在！请重新输入。");
                }
            } else {
                coDao.setAddrPNUll(coDto.getCoId());
            }
            if (addrs.length > 1 && ToolsUtils.notEmpty(addrs[1])) {
                SysDictDataDto sysDictDataDto = sysDictDataService.selectOne(new EntityWrapper<SysDictDataDto>()
                        .eq("dict_type", Constant.SYS_DICT_TYPE_CITY)
                        .eq("dict_value", addrs[1]));
                if (ToolsUtils.notEmpty(sysDictDataDto)) {
                    coDto.setCoAddrC(sysDictDataDto.getDictCode());
                } else {
                    throw new ExcelException("该公司城市地址不存在！请重新输入。");
                }
            } else {
                coDao.setAddrCNUll(coDto.getCoId());
            }
        }
        if (ToolsUtils.isEmpty(coDto.getRemark())) {
            coDao.setRemarkNull(coDto.getCoId());
        }

        ToolsUtils.setUpdateUserAndDate(coDto);
        coDao.updateById(coDto);

        //校验修改手机号是否重复
        SysUserDto mobileCheck = sysUserService.selectOne(new EntityWrapper<SysUserDto>().eq("mobile", coDto.getPicMobile()));

        //手机号重复判断用户id是否一致
        if (ToolsUtils.notEmpty(mobileCheck) && !(mobileCheck.getUserId().equals(sysUserDto.getUserId()))) {
            throw new ExcelException("负责人手机号：" + coDto.getPicMobile() + "重复，请确认。");
        } else {
            if (coDto.getPicMobile().length() != 11) {
                throw new ExcelException("该手机号长度不正确！");
            } else if (!(ToolsUtils.checkMobileFormat(coDto.getPicMobile()))) {
                throw new ExcelException("请检查手机号是否正确");
            }
        }
        SysUserDto updateUser = new SysUserDto();
        updateUser.setMobile(coDto.getPicMobile());
        updateUser.setUserCode(coDto.getUserName());
        updateUser.setUserName(coDto.getUserName());
        updateUser.setUserId(sysUserDto.getUserId());
        ToolsUtils.setUpdateUserAndDate(updateUser);

        sysUserService.updateById(updateUser);
        return coDto;
    }

    @Override
    public String getCoCode() {
        Long incrNum = redisUtils.incr("COCODE");
        if (1 == incrNum) {
            incrNum = getMaxCoCode();
            redisUtils.set("COCODE", incrNum);
        }
        return String.format("%08d", incrNum);
    }

    @Override
    public CoDto getCo(CoDto coDto) {
        return coDao.getCo(coDto);
    }

    /**
     * 修改机选机构及其对应的动态字段
      * @Title CoServiceImpl
      * @Description
      * @author MyPC
      * @date 2019年07月05日 16:17
      * @version V1.0
      * @param coDto
      * @return void
      * @throws
      */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateMachineCo(CoDto coDto) throws Exception {
        ToolsUtils.setUpdateUserAndDate(coDto);
        if(ToolsUtils.isEmpty(coDto.getCoAddrP())){
            coDao.setAddrPNUll(coDto.getCoId());
        }
        if(ToolsUtils.isEmpty(coDto.getCoAddrC())){
            coDao.setAddrCNUll(coDto.getCoId());
        }
        if(ToolsUtils.isEmpty(coDto.getRemark())){
            coDao.setRemarkNull(coDto.getCoId());
        }
        Boolean flag = this.updateById(coDto);
        if (!flag) {
            throw new CustomException("更新失败(Update Failure)");
        }

        // 机选机构的有效状态与用户同步
        SysUserDto sysUserDto = new SysUserDto();
        sysUserDto.setUserId(coDto.getUserId());
        if (Constant.UN_VALID_IND.equals(coDto.getValidInd())) {
            sysUserDto.setValidInd(Constant.UN_VALID_IND);
        } else if (Constant.VALID_IND.equals(coDto.getValidInd())) {
            sysUserDto.setValidInd(Constant.VALID_IND);
        }
        ToolsUtils.setUpdateUserAndDate(sysUserDto);
        sysUserService.updateById(sysUserDto);

        List<BusDimValDto> busDimValDtoList = busDimValDao.selectList(new EntityWrapper<BusDimValDto>().eq("bus_id", coDto.getCoId()).eq("bus_type", Constant.DimValBusType.MACHINECO.getCode()).eq("valid_ind", Constant.VALID_IND));

        //获取历史创建机选规则的用户与创建时间
        String creatorCode = null;
        Date insertTimeForHis = null;
        if (busDimValDtoList != null && busDimValDtoList.size() > 0) {
            creatorCode = busDimValDtoList.get(0).getCreatorcode();
            insertTimeForHis = busDimValDtoList.get(0).getInserttimeforhis();
            for (BusDimValDto busDimValDto : busDimValDtoList) {
                ToolsUtils.setUpdateUserAndDate(busDimValDto);
                busDimValDto.setValidInd(Constant.UN_VALID_IND);
                busDimValDto.setOperateStatus(Constant.DELETE_STATUS);
                this.busDimValDao.updateById(busDimValDto);
            }
        }

        List<BusDimValDto> busDimValList = coDto.getBusDimValList();
        if (busDimValList != null && busDimValList.size() > 0) {
            for (BusDimValDto busDimValDto : busDimValList) {
                if (StringUtils.isNotBlank(busDimValDto.getDimVal())) {
                    //若存在没有值的数据就取当前登录用户
                    if (ToolsUtils.notEmpty(creatorCode) || ToolsUtils.notEmpty(insertTimeForHis)) {
                        //设置历史创建机选规则的用户与创建时间
                        busDimValDto.setCreatorcode(creatorCode);
                        busDimValDto.setInserttimeforhis(insertTimeForHis);
                        ToolsUtils.setUpdateUserAndDate(busDimValDto);
                    } else {
                        ToolsUtils.setUserAndDate(busDimValDto);
                    }
                    busDimValDto.setValidInd(Constant.VALID_IND);
                    busDimValDto.setOperateStatus(Constant.NORMAL_STATUS);
                    busDimValDto.setBusId(coDto.getCoId());
                    busDimValDto.setBusType(Constant.DimValBusType.MACHINECO.getCode());
                    this.busDimValDao.insert(busDimValDto);
                }
            }
        }
    }

    /**
     * 查询最大的公司代码，不存在则返回1
     * @return
     */
    private Long getMaxCoCode(){
        String MaxNo = dimDao.getCurMaxCode("t_cip_co","co_code");
        if (StringUtil.isNotBlank(MaxNo)) {
            return Long.valueOf(MaxNo.substring(2))+1;
        }
        return new Long(1);
    }

    /**
     * 构建机选机构导入模板Excel的表头信息
     * @return
     */
    private List<JSONObject> buildMacinTemplateRowNames(String tmplId) {
        List<JSONObject> rowNames = new ArrayList<>();
        JSONObject rowName;
        for (MachineRowName item : MachineRowName.values()) {
            rowName = new JSONObject();
            rowName.put("excelName", item.getExcelName());
            rowName.put("name", item.getName());
            rowName.put("need", item.getNeed());
            rowNames.add(rowName);
        }
        TmplDto tmplDto = tmplService.getTmplById(tmplId);
        TMPLDTO = tmplDto;
        List<TmplDimDto> tmplDimDtoList = tmplDto.getTmplDimList();
        if (ToolsUtils.isEmpty(tmplDimDtoList)) {
            return rowNames;
        }
        //动态字段
        for (TmplDimDto tmplDimDto : tmplDimDtoList) {
            String fieldName = "fieldA" + (char) (tmplDimDto.getSortNo() + 64);
            rowName = new JSONObject();
            if (ToolsUtils.notEmpty(tmplDimDto) && ToolsUtils.notEmpty(tmplDimDto.getDimDto())) {
                rowName.put("excelName", tmplDimDto.getDimDto().getDimName());
                rowName.put("name", fieldName);
                rowName.put("need", tmplDimDto.getIsNeed());
                rowNames.add(rowName);
            }
        }
        return rowNames;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void tranToMach(List<BusDimValDto> busDimValList) throws Exception {
        CoDto updateCo = new CoDto();
        ToolsUtils.setUpdateUserAndDate(updateCo);
        // 转成机选机构
        updateCo.setCoType("02");
        updateCo.setCoId(busDimValList.get(0).getBusId());
        coDao.updateById(updateCo);

        for (BusDimValDto busDimValDto : busDimValList) {
            ToolsUtils.setUserAndDate(busDimValDto);
            busDimValDto.setOperateStatus("1");
            busDimValDto.setValidInd("1");
            busDimValDto.setBusType("1");
            busDimValService.insert(busDimValDto);
        }
    }
}