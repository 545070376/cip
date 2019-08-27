package io.piccsz.common.sms;

import com.alibaba.fastjson.JSON;
import io.piccsz.common.utils.*;
import io.piccsz.modules.api.customer.dto.ShortMsgRequest;
import io.piccsz.modules.api.customer.dto.ShortMsgRequestBodyDto;
import io.piccsz.modules.api.customer.dto.ShortMsgRequestHeadDto;
import io.piccsz.modules.api.customer.dto.ShortMsgResponse;
import io.piccsz.modules.sys.dao.SysDictDataDao;
import io.piccsz.modules.sys.dao.SysIntfLogDao;
import io.piccsz.modules.sys.dto.custom.SysDictDataDto;
import io.piccsz.modules.sys.dto.custom.SysIntfLogDto;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import java.util.Date;
import java.util.Random;

/**
 * @Author: bin.chen
 * @Date: 2019/5/27
 * @Description: 短信工具类
 */
@Component
public class SmsUtils {

    private static Logger logger = LoggerFactory.getLogger(SmsUtils.class);

    /** 请求的接口类型代码 */
    private static String appId;
    @Value("${piccsz.appId}")
    public void setAppId(String appId) {
        SmsUtils.appId = appId;
    }

    /** 分配给保险公司或保险公司自行设定的token值 */
    private static String token;
    @Value("${piccsz.token}")
    public void setToken(String token) {
        SmsUtils.token = token;
    }

    /** 短信发送地址 */
    private static String msgUrl;
    @Value("${piccsz.msg.send}")
    public void setMsgUrl(String msgUrl) {
        SmsUtils.msgUrl = msgUrl;
    }

    private static SysIntfLogDao sysIntfLogDao;
    @Autowired
    public void setSysIntfLogDao(SysIntfLogDao sysIntfLogDao) {
        SmsUtils.sysIntfLogDao = sysIntfLogDao;
    }

    private static SysDictDataDao sysDictDataDao;
    @Autowired
    public void setSysDictDataDao(SysDictDataDao sysDictDataDao) {
        SmsUtils.sysDictDataDao = sysDictDataDao;
    }

    public static boolean sendShortMessage(String content, String mobile) {
        ThreadPoolUtils.exec(() -> {
            try {
                sendShortMessageSync(content,mobile);
            } catch (Exception e) {
                logger.info("手机号{}:,发送短信失败，原因是：{}",mobile,ExceptionUtils.getExceptionStackTraceString(e));
            }
        });
        return true;
    }

    /**
     * 发送短信接口
     * @param content 短信内容
     * @param mobile 手机号
     * @return
     */
    public static boolean sendShortMessageSync(String content, String mobile) {
        /************* 封装请求短信系统接口报文 Start *************/

        // 接口日志初始化
        SysIntfLogDto sysIntfLogDto = new SysIntfLogDto();
        sysIntfLogDto.setIntfType(Constant.IntfType.SMS.getCode());
        sysIntfLogDto.setStartTime(new Date());
        SysDictDataDto sysDictDataDto = new SysDictDataDto();
        sysDictDataDto.setDictType(Constant.DEFAULT_ADMINISTRATOR);
        sysDictDataDto = sysDictDataDao.selectOne(sysDictDataDto);
        StringBuffer faild_reason = new StringBuffer();

        ShortMsgRequest request = new ShortMsgRequest();
        ShortMsgRequestHeadDto requestHeadDto = new ShortMsgRequestHeadDto();
        buildRequestHead(requestHeadDto);
        request.setRequesthead(requestHeadDto);

        ShortMsgRequestBodyDto requestbody = new ShortMsgRequestBodyDto();
        requestbody.setContent(content);
        requestbody.setMobile(mobile);
        request.setRequestbody(requestbody);
        /************* 封装请求短信系统接口报文 End *************/
        // 获取时间戳
        String timestamp = Constant.formatDate.format(new Date());
        // 生成随机数
        long nonce = IdGen.randomLong();
        // 封装SHA-1签名
        String signature = Sha1Utils.sign(token, timestamp, String.valueOf(nonce), appId);
        // 拼接文件上传地址参数
        String serverURL = msgUrl + "?Timestamp=" + timestamp + "&nonce=" + nonce + "&signature=" + signature;
        logger.info("短信请求报文：{}\n请求地址：{}", JSON.toJSONString(request), serverURL);
        sysIntfLogDto.setRequestXml(JSON.toJSONString(request));
        String[] result = ClientUtils.connectOpenServer(serverURL, request);
        boolean flag = true;
        if (Constant.SUCCESS.equals(result[0])) {
            logger.info("短信接口返回数据：{}", result[1]);
            ShortMsgResponse shortMsgResponse = JSON.parseObject(result[1], ShortMsgResponse.class);
            sysIntfLogDto.setResponseXml(result[1]);
            if (Constant.MsgIntfCode.SUCCESS.getCode().equals(shortMsgResponse.getResultCode())) {
                flag = true;
            } else if(Constant.MsgIntfCode.ERROR_CODE1.getCode().equals(shortMsgResponse.getResultCode())){
                logger.error("短信请求接口系统失败，失败原因：{}", Constant.MsgIntfCode.ERROR_CODE1.getName());
                faild_reason.append("短信请求接口系统失败，失败原因：{").append(Constant.MsgIntfCode.ERROR_CODE1.getName()).append("}");
                flag = false;
            } else if(Constant.MsgIntfCode.ERROR_CODE2.getCode().equals(shortMsgResponse.getResultCode())){
                logger.error("短信请求接口系统失败，失败原因：{}", Constant.MsgIntfCode.ERROR_CODE2.getName());
                faild_reason.append("短信请求接口系统失败，失败原因：{").append(Constant.MsgIntfCode.ERROR_CODE2.getName()).append("}");
                flag = false;
            }else if(Constant.MsgIntfCode.ERROR_CODE3.getCode().equals(shortMsgResponse.getResultCode())){
                logger.error("短信请求接口系统失败，失败原因：{}", Constant.MsgIntfCode.ERROR_CODE3.getName());
                faild_reason.append("短信请求接口系统失败，失败原因：{").append(Constant.MsgIntfCode.ERROR_CODE3.getName()).append("}");
                flag = false;
            }else if(Constant.MsgIntfCode.ERROR_CODE4.getCode().equals(shortMsgResponse.getResultCode())){
                logger.error("短信请求接口系统失败，失败原因：{}", Constant.MsgIntfCode.ERROR_CODE4.getName());
                faild_reason.append("短信请求接口系统失败，失败原因：{").append(Constant.MsgIntfCode.ERROR_CODE4.getName()).append("}");
                flag = false;
            }
        } else {
            logger.error("短信请求接口处理异常，失败原因：" + result[1]);
            faild_reason.append("短信请求接口处理异常，失败原因：").append(result[1]);
            flag = false;
        }
        if(flag == true) {
            sysIntfLogDto.setStatus("0");
        } else {
            sysIntfLogDto.setStatus("1");
        }
        sysIntfLogDto.setFaildReason(faild_reason.toString());
        sysIntfLogDto.setEndTime(new Date());
        sysIntfLogDto.setCreatorcode(String.valueOf(sysDictDataDto.getDictCode()));
        sysIntfLogDto.setInserttimeforhis(new Date());
        sysIntfLogDto.setUpdatercode(String.valueOf(sysDictDataDto.getDictCode()));
        sysIntfLogDto.setOperatetimeforhis(new Date());
        sysIntfLogDao.insert(sysIntfLogDto);
        return flag;
    }

    /**
     * 组装请求报文头
     * @param requestHeadDto
     */
    private static void buildRequestHead(ShortMsgRequestHeadDto requestHeadDto){
        requestHeadDto.setRequestType(Constant.MSG_REQUEST_TYPE);
        requestHeadDto.setRequestId(IdGen.uuid());
        requestHeadDto.setRequestTime(new Date());
        requestHeadDto.setAppId(appId);
    }

    /**
     * 获取随机验证码
     * @return
     */
    public static String getValidCode() {
        Random random = new Random();
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 6; i++) {
            int c = random.nextInt(10);
            sb.append(c);
        }
        return sb.toString();
    }
}
