package com.htkapp.modules.merchant.common.web;

import com.htkapp.core.OtherUtils;
import com.htkapp.core.customShiro.CusTokenManage;
import com.htkapp.core.jsAjax.AjaxResponseModel;
import com.htkapp.core.params.RequestParams;
import com.htkapp.core.shiro.common.utils.LoggerUtils;
import com.htkapp.core.shiro.common.utils.StringUtils;
import com.htkapp.core.shiro.core.shiro.token.manager.TokenManager;
import com.htkapp.core.utils.Globals;
import com.htkapp.modules.common.dto.AjaxReturnLoginData;
import com.htkapp.modules.common.entity.LoginUser;
import com.htkapp.modules.merchant.common.service.IndexService;
import com.htkapp.modules.merchant.common.service.MerchantService;
import com.htkapp.modules.merchant.shop.entity.AccountShop;
import com.htkapp.modules.merchant.shop.entity.AccountShopReplyComments;
import com.htkapp.modules.merchant.shop.entity.Shop;
import com.htkapp.modules.merchant.shop.service.ShopServiceI;
import com.sun.org.apache.bcel.internal.generic.FADD;
import com.sun.org.apache.xpath.internal.operations.Mod;
import com.xiaoleilu.hutool.date.DateUtil;
import com.xiaoleilu.hutool.lang.Base64;
import org.directwebremoting.guice.RequestParameters;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import redis.clients.jedis.JedisPool;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpSession;
import javax.validation.Valid;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static com.xiaoleilu.hutool.date.DateUtil.*;

/**
 * Created by yinqilei on 17-7-11.
 */

@Controller
@RequestMapping("/merchant/")
public class MerchantController {

    @Resource
    private MerchantService merchantService;
    @Resource
    private OtherUtils otherUtilsMethod;

    public final static String mDirectory = "merchant/";


    //==============================================================商户登陆、退出、主页
    //商户登陆页面GET
    @RequestMapping(value = "/login", method = RequestMethod.GET)
    public String merchantLogin(String userName, Model model) {
        if (StringUtils.isNotEmpty(userName)) {
            model.addAttribute("userName", userName);
        }
        try {
            HttpServletRequest request = otherUtilsMethod.getRequestByMethod();
            if(request != null){
                HttpSession session = request.getSession();
                Object object = session.getAttribute(Globals.MERCHANT_SESSION_USER);
                if(object != null){
                    //重定向到主页
                    return "redirect:index";
                }
            }
        }catch (Exception e){
            return mDirectory + "login";
        }
        model.addAttribute("date", new Date().getTime());
        return mDirectory + "login";
    }

    //商户登陆页面POST
    @RequestMapping(value = "/login", method = RequestMethod.POST)
    @ResponseBody
    public AjaxResponseModel merchantLogin(LoginUser loginUser, String loginVCode) {
        try {
            HttpServletRequest request = otherUtilsMethod.getRequestByMethod();
            HttpSession session = request.getSession();
            String verifyCode = (String) session.getAttribute(Globals.MERCHANT_KAPTCHAT_KEY);
            if (loginVCode.equals(verifyCode)) {
                loginUser = CusTokenManage.loginByUser(loginUser, loginUser.getRememberMe());
                Date endTime = DateUtil.parse(loginUser.getUseEndTime());
                Date nowTime = new Date();
                if(nowTime.getTime() > endTime.getTime()){
                    return new AjaxResponseModel(Globals.COMMON_OPERATION_FAILED, "帐号使用时间过期");
                }
                session.setAttribute(Globals.MERCHANT_SESSION_USER, loginUser);
                AjaxReturnLoginData returnData = new AjaxReturnLoginData(loginUser.getUserName(), loginUser.getPassword(), loginUser.getRole(), "/merchant/index", loginUser.getToken());
                return new AjaxResponseModel<>(Globals.COMMON_SUCCESS_AND_JUMP_URL, "成功", returnData, "/merchant/index");
            } else {
                return new AjaxResponseModel(Globals.COMMON_OPERATION_FAILED, "验证码错误,请重新输入");
            }
        } catch (Exception e) {
            return new AjaxResponseModel(Globals.COMMON_OPERATION_FAILED, e.getMessage());
        }
    }

    //商户退出登陆POST
    @RequestMapping(value = "/logout", method = RequestMethod.POST)
    @ResponseBody
    public AjaxResponseModel merchantSignOut() {
        try {
            CusTokenManage.logoutUser();
            return new AjaxResponseModel<String>(Globals.COMMON_SUCCESS_AND_JUMP_URL, " 退出登陆成功", null, "/merchant/login");
        } catch (Exception e) {
            return new AjaxResponseModel<>(Globals.COMMON_OPERATION_FAILED, "退出失败");
        }
    }

    //商户主页(homePage)
    @RequestMapping(value = "/index", method = RequestMethod.GET)
    public String index(Model model, RequestParams params) {
        try {
            Map<String, Object> map = new HashMap<>();
            map.put("m_index", true);
            map.put("date", new Date().getTime());
            map.put("cacheMessage","缓存字符串");
            OtherUtils.ReturnValByModel(model, map);
            params.setModel(model);
            merchantService.getHomePage(params);
        }catch (Exception e){
            LoggerUtils.fmtError(getClass(),e,e.getMessage());
        }
        return mDirectory + "hmPage";
    }

    //通知中心－消息
    @RequestMapping(value = "/getNotificationCenterView", method = RequestMethod.GET)
    public String getNotificationCenterView(Model model,
                                            @RequestParam(value = "pageNo", required = false, defaultValue = "1") String pageNo,
                                            @RequestParam(value = "status", required = false, defaultValue = "99") String status) {
        try {
            LoginUser user = OtherUtils.getLoginUserByRequest();
            int pageNumber = 1;
            if (!pageNo.equals("1")) {
                String decodeStrResult = Base64.decodeStr(pageNo);
                if (OtherUtils.verifyStrIsNumber(decodeStrResult)) {
                    pageNumber = Integer.parseInt(decodeStrResult);
                }
            }
            int mesStatus = 99;
            if (!status.equals("99")) {
                String decodeStrResult = Base64.decodeStr(status);
                if (OtherUtils.verifyStrIsNumber(decodeStrResult)) {
                    mesStatus = Integer.parseInt(decodeStrResult);
                }
            }
            Map<String, Object> map = new HashMap<>();
            map.put("date", new Date().getTime());
            map.put("status", mesStatus);
            OtherUtils.ReturnValByModel(model, map);
            merchantService.getNotifyDataByToken(model, user.getToken(), pageNumber, mesStatus);
        } catch (Exception e) {
            e.printStackTrace();
        }
        return mDirectory + "notify_center";
    }

    //注册页面
    @RequestMapping(value = "/register", method = RequestMethod.GET)
    public String registerPage(Model model){
        model.addAttribute("date", new Date().getTime());
        return mDirectory + "register";
    }

    //商户注册页面提交页面接口
    @RequestMapping(value = "/register", method = RequestMethod.POST)
    @ResponseBody
    public AjaxResponseModel submitRegisterBYyPost(AccountShop accountShop, RequestParams params) {
        return merchantService.submitRegisterBYyPost(accountShop, params);
    }

    //找回密码确认身份成功后，更改密码接口
    @RequestMapping(value = "/changePassword", method = RequestMethod.GET)
    @ResponseBody
    public AjaxResponseModel changePassword(String phone, String password, String code) {
        return merchantService.changePassword(phone, password, code);
    }

    //异步验证商户账号是否存在接口
    @RequestMapping(value = "/verifyMerchant", method = RequestMethod.POST)
    @ResponseBody
    public AjaxResponseModel verifyMerchant(String phone) {
        return merchantService.verifyMerchant(phone);
    }

    //异步验证用户输入手机号和验证码是否正确接口
    @RequestMapping(value = "/verifyMobileAndPhone", method = RequestMethod.POST)
    @ResponseBody
    public AjaxResponseModel verifyMobilePhoneAndCode(String phone, String code) {
        return merchantService.verifyMobilePhoneAndCode(phone, code);
    }

    //改变店铺状态接口
    @RequestMapping(value = "/changeShopState_Page", method = RequestMethod.POST)
    @ResponseBody
    public AjaxResponseModel changeShopState(HttpServletRequest request, int stateId) {
        return merchantService.changeShopState(request, stateId);
    }

    //改变通知消息状态接口
    @RequestMapping(value = "/changeMsgStatus", method = RequestMethod.POST)
    @ResponseBody
    public AjaxResponseModel changeMsgStatus(Integer id,
                                             @RequestParam(value = "statusCode", required = false, defaultValue = "1") Integer statusCode) {
        return merchantService.changeNotificationMessageStatus(id, statusCode);
    }

    //插入商户回复用户评论接口
    @RequestMapping(value = "/replyMessageGiveUser", method = RequestMethod.POST)
    @ResponseBody
    public AjaxResponseModel replyMessageGiveUser(AccountShopReplyComments replyComments) {
        return merchantService.replyMessageGiveUser(replyComments);
    }

    //商户提现到支付宝接口

    //======================================top菜单栏url开始
    //商品
    //====外卖
    @RequestMapping(value = "/takeout/product/homePage", method = RequestMethod.GET)
    public String releaseIndex(Model model) {
        Map<String, Object> map = new HashMap<>();
        map.put("pro_mark", true);
        map.put("pro_mark_t", true);
        map.put("date", new Date().getTime());
        OtherUtils.ReturnValByModel(model, map);
        return mDirectory + "product_takeout_index";
    }

    //====团购
    @RequestMapping(value = "/groupBuy/product/homePage", method = RequestMethod.GET)
    public String groupBuyIndex(Model model, RequestParams params,
                                @RequestParam(value = "pageNum", required = false, defaultValue = "1") Integer pageNum) {
        Map<String, Object> map = new HashMap<>();
        map.put("pro_mark", true);
        map.put("pro_mark_g", true);
        map.put("date", new Date().getTime());
        OtherUtils.ReturnValByModel(model, map);
        params.setModel(model);
        params.setPageNum(pageNum);
        merchantService.groupBuyProductIndex(params);
        return mDirectory + "product_groupBuy_index";
    }

    //====自助点餐
    @RequestMapping(value = "/buffetFood/product/homePage", method = RequestMethod.GET)
    public String buffetFoodIndex(Model model) {
        Map<String, Object> map = new HashMap<>();
        map.put("pro_mark", true);
        map.put("pro_mark_b", true);
        map.put("date", new Date().getTime());
        OtherUtils.ReturnValByModel(model, map);
        return mDirectory + "product_buffetFood_index";
    }

    //订单
    //====外卖订单(历史订单)
    @RequestMapping(value = "/takeout/order/historyTakeoutOrder", method = RequestMethod.GET)
    public String historyTakeoutOrder(Model model, String dateStart, String dateEnd,
                                      @RequestParam(value = "pageNo", required = false, defaultValue = "1") int pageNo) {
        //查找数据，放入Model传到页面  0是外卖
        Map<String, Object> map = new HashMap<>();
        String startDate = DateUtil.format(DateUtil.offsetDay(DateUtil.parse(DateUtil.format(new Date(), NORM_DATE_PATTERN)), -5), NORM_DATE_PATTERN);
        String endDate = DateUtil.format(new Date(), NORM_DATE_PATTERN);
        map.put("ord_mark", true);
        map.put("ord_mark_t", true);
        map.put("date", new Date().getTime());
        if(StringUtils.isNotEmpty(dateStart) && StringUtils.isNotEmpty(dateEnd)){
            //判断开始时间是否大于结束时间
            if(DateUtil.parse(dateStart).getTime() > DateUtil.parse(dateEnd).getTime()){
                model.addAttribute("messg", "开始时间不能大于结束时间,请重新选择时间");
            }else {
                startDate = dateStart;
                endDate = dateEnd;
            }
        }

        map.put("dateStart", startDate);
        map.put("dateEnd", endDate);

        //已接订单数
        map.put("orderCount", 5);
        //今日营页额
        map.put("turnoverToday", 20.00);
        //待发货订单数
        map.put("pendingOrder", 2);
        OtherUtils.ReturnValByModel(model, map);
        merchantService.getOrderListByAIdAndMark(0, model, pageNo, startDate, endDate);
        return mDirectory + "order_takeout_history";
    }

    //====外卖订单（实时订单）
    @RequestMapping(value = "/takeout/order/realTimeTakeoutOrder", method = RequestMethod.GET)
    public String realTimeTakeoutOrder(Model model,
                                       @RequestParam(value = "statusCode", required = false, defaultValue = "0") Integer statusCode) {
        //默认查找新订单,并返回数据给前台
        //前端点击事件处理，get方式请求后台查询数据，并把查询的条件返回给前台，显示上一请求后台的条件

        //查询条件 商铺id,时间, 状态(新订单0，已处理(1,2,3,4,5))
        Map<String, Object> map = new HashMap<>();
        map.put("date", new Date().getTime());

        map.put("ord_mark", true);

        map.put("ord_mark_t", true);

        map.put("statusCode", statusCode);

        OtherUtils.ReturnValByModel(model, map);

        //默认查找新订单下的数据，并置页面筛选条件为新订单
        //查询新订单(当天24小时内的订单)

        //点击新订单，再以get方式查找数据

        //点击已处理， 再以get方式查找数据

        String startDate = DateUtil.beginOfDay(new Date()).toString();
        String endDate = DateUtil.endOfDay(new Date()).toString();
        merchantService.getTakeoutRealTimeOrderByCondition(model, 1, startDate, endDate, statusCode);
        return mDirectory + "order_takeout_realTime";
    }

    //====自助点餐订单查询(未结算订单)
    @RequestMapping(value = "/buffetFood/order/query", method = RequestMethod.GET)
    public String buffetFoodOrderQuery(Model model,
                                       @RequestParam(value = "pageNum", required = false, defaultValue = "1") Integer pageNum) {
        Map<String, Object> map = new HashMap<>();
        map.put("date", new Date().getTime());
        map.put("ord_mark", true);
        map.put("ord_mark_b_q", true);
        OtherUtils.ReturnValByModel(model, map);
        merchantService.getBuffetFoodOrderQueryResult(model, pageNum);
        return mDirectory + "order_buffetFood_query";
    }

    //====自助点餐订单查询(已结算订单)
    @RequestMapping(value = "/buffetFood/order/done", method = RequestMethod.GET)
    public String buffetFoodOrderDone(Model model, String startTime, String endTime,
                                      @RequestParam(value = "pageNum", required = false, defaultValue = "1") Integer pageNum) {
        Map<String, Object> map = new HashMap<>();
        map.put("date", new Date().getTime());
        map.put("ord_mark", true);
        map.put("ord_mark_b_q", true);
        OtherUtils.ReturnValByModel(model, map);
        if (StringUtils.isNotEmpty(startTime) && StringUtils.isNotEmpty(endTime)) {
            //不为空
            endTime = format(DateUtil.endOfDay(DateUtil.parseDate(endTime)), NORM_DATETIME_PATTERN);
        }
        merchantService.getBuffetFoodOrderDoneResult(model, startTime, endTime, pageNum);
        return mDirectory + "order_buffetFood_done";
    }

    //====自助点餐订单处理(新订单)
    @RequestMapping(value = "/buffetFood/order/new", method = RequestMethod.GET)
    public String buffetFoodOrderNew(Model model,RequestParams params,
                                     @RequestParam(value = "pageNum", required = false, defaultValue = "1") Integer pageNum) {
        Map<String, Object> map = new HashMap<>();
        map.put("date", new Date().getTime());
        map.put("ord_mark", true);
        map.put("ord_mark_b_h", true);
        OtherUtils.ReturnValByModel(model, map);
        params.setModel(model);
        params.setPageNum(pageNum);
        merchantService.buffetFoodNewOrder(params);
        return mDirectory + "order_buffetFood_new";
    }

    //====自助点餐订单处理(订单调整)
    @RequestMapping(value = "/buffetFood/order/edit", method = RequestMethod.GET)
    public String buffetFoodOrderEdit(Model model, RequestParams params,
                                      @RequestParam(value = "pageNum", required = false, defaultValue = "1") Integer pageNum) {
        Map<String, Object> map = new HashMap<>();
        map.put("date", new Date().getTime());
        map.put("ord_mark", true);
        map.put("ord_mark_b_h", true);
        OtherUtils.ReturnValByModel(model, map);
        params.setPageNum(pageNum);
        params.setModel(model);
        merchantService.buffetFoodOrderEdit(params);
        return mDirectory + "order_buffetFood_edit";
    }

    //====自助点餐订单处理(催单)
    @RequestMapping(value = "/buffetFood/order/reminder", method = RequestMethod.GET)
    public String buffetFoodOrderReminder(Model model, RequestParams params,
                                          @RequestParam(value = "pageNum", required = false, defaultValue = "1") Integer pageNum) {
        Map<String, Object> map = new HashMap<>();
        map.put("date", new Date().getTime());
        map.put("ord_mark", true);
        map.put("ord_mark_b_h", true);
        OtherUtils.ReturnValByModel(model, map);
        params.setPageNum(pageNum);
        params.setModel(model);
        merchantService.buffetFoodOrderReminder(params);
        return mDirectory + "order_buffetFood_reminder";
    }


    //售后评论
    //====外卖评论
    @RequestMapping(value = "/takeout/comment/index", method = RequestMethod.GET)
    public String takeoutCommentIndex(Model model,
                                      @RequestParam(value = "date", required = false, defaultValue = "0") String date,
                                      @RequestParam(value = "starRating", required = false, defaultValue = "0") String starRating,
                                      @RequestParam(value = "pageNum", required = false, defaultValue = "1") Integer pageNum) {
        Map<String, Object> map = new HashMap<>();
        map.put("date", new Date().getTime());
        map.put("com_mark", true);
        map.put("com_mark_t", true); //外卖评论
        OtherUtils.ReturnValByModel(model, map);
        //综合评分数
        //星级回复百分比
        //评论回复率、差评回复率
        //根据日期和星级和分页查找评论数据
        //评论数据(是否评论，回复内容)
        //分页数据
        try {
            LoginUser user = OtherUtils.getLoginUserByRequest();

            String startTime = "2017-08-04 00:00:00";
            String endTime = "2017-08-05 23:59:59";

            //处理日期字符串
            if (StringUtils.isNotEmpty(date)) {
                if (OtherUtils.verifyStrIsNumber(date)) {
                    //字符串是数字
                    //0是昨天　1是近七天  2是近30天  3是近90天
                    Map<String, String> dateMap = OtherUtils.getDateMapByNum(Integer.parseInt(date));
                    if (dateMap.size() > 0) {
                        startTime = dateMap.get("startTime");
                        endTime = dateMap.get("endTime");
                    }
                } else {
                    //字符串不是数字,　验证字符的最后一位是否是分隔符
                    String lastStr = date.substring(date.length() - 1);
                    if (lastStr.equals("~")) {
                        System.out.println("重复了");
                        //最后一位包含分隔符,去除最后一个字符串
                        String dateStr = date.substring(0, date.length() - 1);
                        String[] dateList = dateStr.split("~");
                        String str1 = dateList[0];
                        String str2 = dateList[1];
                        Date time1 = DateUtil.parse(str1);
                        Date time2 = DateUtil.parse(str2);
                        if (time1.getTime() < time2.getTime()) {
                            startTime = str1;
                            endTime = format(DateUtil.endOfDay(DateUtil.parseDate(str2)), NORM_DATETIME_PATTERN);
                        } else {
                            startTime = str2;
                            System.out.println("ddddd");
                            endTime = format(DateUtil.endOfDay(DateUtil.parseDate(str1)), NORM_DATETIME_PATTERN);
                        }
                    } else {
                        //不包含
                        System.out.println("重复了");
                        String[] dateList = date.split("~");
                        String str1 = dateList[0];
                        String str2 = dateList[1];
                        Date time1 = DateUtil.parse(str1);
                        System.out.println("dddd");
                        Date time2 = DateUtil.parse(str2);
                        if (time1.getTime() < time2.getTime()) {
                            startTime = str1;
                            endTime = format(DateUtil.endOfDay(DateUtil.parseDate(str2)), NORM_DATETIME_PATTERN);
                        } else {
                            startTime = str2;
                            endTime = format(DateUtil.endOfDay(DateUtil.parseDate(str1)), NORM_DATETIME_PATTERN);
                            ;
                        }
                    }
                }
            }
            merchantService.getTakeoutCommentListDataByToken(model, user.getToken(), starRating, startTime, endTime, pageNum);
            model.addAttribute("queryDate", date);
            model.addAttribute("starRating", starRating);
        } catch (Exception e) {
            model.addAttribute("data", null);
        }
        return mDirectory + "comment_takeout_index";
    }

    //====团购评论
    @RequestMapping(value = "/groupBuy/comment/index", method = RequestMethod.GET)
    public String groupBuyCommentIndex(Model model,
                                       @RequestParam(value = "date", required = false, defaultValue = "0") String date,
                                       @RequestParam(value = "starRating", required = false, defaultValue = "0") String starRating,
                                       @RequestParam(value = "pageNum", required = false, defaultValue = "1") Integer pageNum) {
        Map<String, Object> map = new HashMap<>();
        map.put("date", new Date().getTime());
        map.put("com_mark", true);
        map.put("com_mark_g", true); //外卖评论
        OtherUtils.ReturnValByModel(model, map);
        //综合评分数
        //星级回复百分比
        //评论回复率、差评回复率
        //根据日期和星级和分页查找评论数据
        //评论数据(是否评论，回复内容)
        //分页数据
        try {
            LoginUser user = OtherUtils.getLoginUserByRequest();

            String startTime = "2017-08-04 00:00:00";
            String endTime = "2017-08-05 23:59:59";

            //处理日期字符串
            if (StringUtils.isNotEmpty(date)) {
                if (OtherUtils.verifyStrIsNumber(date)) {
                    //字符串是数字
                    //0是昨天　1是近七天  2是近30天  3是近90天
                    Map<String, String> dateMap = OtherUtils.getDateMapByNum(Integer.parseInt(date));
                    if (dateMap.size() > 0) {
                        startTime = dateMap.get("startTime");
                        endTime = dateMap.get("endTime");
                    }
                } else {
                    //字符串不是数字,　验证字符的最后一位是否是分隔符
                    String lastStr = date.substring(date.length() - 1);
                    if (lastStr.equals("~")) {
                        System.out.println("重复了团购");
                        //最后一位包含分隔符,去除最后一个字符串
                        String dateStr = date.substring(0, date.length() - 1);
                        String[] dateList = dateStr.split("~");
                        String str1 = dateList[0];
                        String str2 = dateList[1];
                        Date time1 = DateUtil.parse(str1);
                        Date time2 = DateUtil.parse(str2);
                        if (time1.getTime() < time2.getTime()) {
                            startTime = str1;
                            endTime = format(DateUtil.endOfDay(DateUtil.parseDate(str2)), NORM_DATETIME_PATTERN);
                        } else {
                            startTime = str2;
                            endTime = format(DateUtil.endOfDay(DateUtil.parseDate(str1)), NORM_DATETIME_PATTERN);
                        }
                    } else {
                        //不包含
                        System.out.println("重复了团购");
                        String[] dateList = date.split("~");
                        String str1 = dateList[0];
                        String str2 = dateList[1];
                        Date time1 = DateUtil.parse(str1);
                        Date time2 = DateUtil.parse(str2);
                        if (time1.getTime() < time2.getTime()) {
                            startTime = str1;
                            endTime = format(DateUtil.endOfDay(DateUtil.parseDate(str2)), NORM_DATETIME_PATTERN);
                        } else {
                            startTime = str2;
                            endTime = format(DateUtil.endOfDay(DateUtil.parseDate(str1)), NORM_DATETIME_PATTERN);
                            ;
                        }
                    }
                }
            }
            merchantService.getGroupBuyCommentListDataByToken(model, user.getToken(), starRating, startTime, endTime, pageNum);
            model.addAttribute("queryDate", date);
            model.addAttribute("starRating", starRating);
        } catch (Exception e) {
            model.addAttribute("data", null);
        }

        return mDirectory + "comment_groupBuy_index";
    }

    //====自助点餐评论
    @RequestMapping(value = "/buffetFood/comment/index", method = RequestMethod.GET)
    public String buffetFoodCommentIndex(Model model,
                                         @RequestParam(value = "date", required = false, defaultValue = "0") String date,
                                         @RequestParam(value = "starRating", required = false, defaultValue = "0") String starRating,
                                         @RequestParam(value = "pageNum", required = false, defaultValue = "1") Integer pageNum) {
        Map<String, Object> map = new HashMap<>();
        map.put("date", new Date().getTime());
        map.put("com_mark", true);
        map.put("com_mark_b", true); //外卖评论
        OtherUtils.ReturnValByModel(model, map);
        //综合评分数
        //星级回复百分比
        //评论回复率、差评回复率
        //根据日期和星级和分页查找评论数据
        //评论数据(是否评论，回复内容)
        //分页数据
        try {
            LoginUser user = OtherUtils.getLoginUserByRequest();

            String startTime = "2017-08-04 00:00:00";
            String endTime = "2017-08-05 23:59:59";

            //处理日期字符串
            if (StringUtils.isNotEmpty(date)) {
                if (OtherUtils.verifyStrIsNumber(date)) {
                    //字符串是数字
                    //0是昨天　1是近七天  2是近30天  3是近90天
                    Map<String, String> dateMap = OtherUtils.getDateMapByNum(Integer.parseInt(date));
                    if (dateMap.size() > 0) {
                        startTime = dateMap.get("startTime");
                        endTime = dateMap.get("endTime");
                    }
                } else {
                    //字符串不是数字,　验证字符的最后一位是否是分隔符
                    String lastStr = date.substring(date.length() - 1);
                    if (lastStr.equals("~")) {
                        System.out.println("重复了");
                        //最后一位包含分隔符,去除最后一个字符串
                        String dateStr = date.substring(0, date.length() - 1);
                        String[] dateList = dateStr.split("~");
                        String str1 = dateList[0];
                        String str2 = dateList[1];
                        Date time1 = DateUtil.parse(str1);
                        Date time2 = DateUtil.parse(str2);
                        if (time1.getTime() < time2.getTime()) {
                            startTime = str1;
                            endTime = format(DateUtil.endOfDay(DateUtil.parseDate(str2)), NORM_DATETIME_PATTERN);
                        } else {
                            startTime = str2;
                            endTime = format(DateUtil.endOfDay(DateUtil.parseDate(str1)), NORM_DATETIME_PATTERN);
                        }
                    } else {
                        //不包含
                        System.out.println("重复了");
                        String[] dateList = date.split("~");
                        String str1 = dateList[0];
                        String str2 = dateList[1];
                        Date time1 = DateUtil.parse(str1);
                        Date time2 = DateUtil.parse(str2);
                        if (time1.getTime() < time2.getTime()) {
                            startTime = str1;
                            endTime = format(DateUtil.endOfDay(DateUtil.parseDate(str2)), NORM_DATETIME_PATTERN);
                        } else {
                            startTime = str2;
                            endTime = format(DateUtil.endOfDay(DateUtil.parseDate(str1)), NORM_DATETIME_PATTERN);
                            ;
                        }
                    }
                }
            }
            merchantService.getBuffetFoodCommentListDataByToken(model, user.getToken(), starRating, startTime, endTime, pageNum);
            model.addAttribute("queryDate", date);
            model.addAttribute("starRating", starRating);
        } catch (Exception e) {
            model.addAttribute("data", null);
        }
        return mDirectory + "comment_buffetFood_index";
    }


    //积分管理
    //=====积分列表
    @RequestMapping(value = "/integral/list", method = RequestMethod.GET)
    public String integralListPage(Model model, String userPhone, @RequestParam(value = "pageNum", required = false, defaultValue = "1") Integer pageNum) {
        Map<String, Object> map = new HashMap<>();
        map.put("date", new Date().getTime());
        map.put("int_mark", true);
        map.put("int_mark_list", true); //账单管理
        OtherUtils.ReturnValByModel(model, map);
        merchantService.integralListDateAndByCondition(model, pageNum, userPhone);
        return mDirectory + "integral_list";
    }

    @RequestMapping(value = "/integral/list", method = RequestMethod.POST)
    public String integralListPagePost(Model model, RequestParams params,
                                       @RequestParam(value = "pageNum", required = false, defaultValue = "1") Integer pageNum) {
        //@Validated  开启注解
        Map<String, Object> map = new HashMap<>();
        map.put("date", new Date().getTime());
        map.put("int_mark", true);
        map.put("int_mark_list", true);
        OtherUtils.ReturnValByModel(model, map);
//        if(result.hasErrors()){
//            System.out.println("验证有错误");
//        }
        merchantService.integralListDateAndByCondition(model, pageNum, params.getUserPhone());
        return mDirectory + "integral_list";
    }

    //=====订座订单
    @RequestMapping(value =  "/integral/seatOrder", method = RequestMethod.GET)
    public String integralSeatOrder(Model model) {
        Map<String, Object> map = new HashMap<>();
        map.put("date", new Date().getTime());
        map.put("int_mark", true);
        map.put("int_mark_seatOrder", true);
        OtherUtils.ReturnValByModel(model, map);
        return mDirectory + "integral_seatOrder";
    }


    //财务管理
    //====账单资金
    @RequestMapping(value = "/bill/billMoney", method = RequestMethod.GET)
    public String billMoney(Model model, @RequestParam(value = "type", required = false, defaultValue = "0") Integer type,
                            @RequestParam(value = "date", required = false, defaultValue = "1") String date,
                            @RequestParam(value = "pageNum", required = false, defaultValue = "0") Integer pageNum) {
        Map<String, Object> map = new HashMap<>();
        map.put("date", new Date().getTime());
        map.put("bil_mark", true);
        map.put("bil_mark_m", true); //账单管理
        OtherUtils.ReturnValByModel(model, map);
        //账户余额
        //====  计算外卖、团购
        //====  当外卖、团购每有一个新订单,都在账单记录表中添加一条记录（外卖只有当确认收货,团购只有当消息才自动归于已入账,其它状态则是属于待入账）
        //====  统计账单记录表中的订单金额为账户余额
        merchantService.getBillMoneyDataByCondition(model, date, type, pageNum);
        //可用余额
        //====　统计已入账的为可用余额
        //待入账余额
        //====  统计未入账的为不可用余额
        //提取入账账户:支付宝账号
        //查找商户信息表(获取用户提现收款账号)
        //账单记录(类型筛选（全部、收入、支出）, 日期（日期按钮、日期控件输入）) 分页  每天算是一条记录
        //=====  根据日期的收入记录(交易完成结算自动归入账户余额)
        //=====  根据日期的支出记录（提现到第三方账户属于余额支出）
        //=====  根据日期的全部类型记录（包含支出、收入）
        model.addAttribute("type", type);
        return mDirectory + "bill_manage";
    }

    //====账单记录
    @RequestMapping(value = "/bill/billRecord", method = RequestMethod.GET)
    public String billRecord(Model model, @RequestParam(value = "pageNum", required = false, defaultValue = "1") Integer pageNum,
                             @RequestParam(value = "date", required = false, defaultValue = "1") String date,
                             @RequestParam(value = "type", required = false, defaultValue = "0") Integer type) {
        Map<String, Object> map = new HashMap<>();
        map.put("date", new Date().getTime());
        map.put("bil_mark", true);
        map.put("bil_mark_r", true); //账单管理
        OtherUtils.ReturnValByModel(model, map);

        //每天账单记录的条目　点击查看详情（查看当天所有订单的详情）
        //入账记录，待入账记录

        model.addAttribute("queryDate", date);
        model.addAttribute("type", type);

        merchantService.getBillRecordListByCondition(model, date, type, pageNum);

        return mDirectory + "bill_record";
    }

    //====账单详情
    @RequestMapping(value = "/bill/billRecord/detail", method = RequestMethod.GET)
    public String billRecordDetail(Model model, String date, Integer recordStatus,
                                   @RequestParam(value = "pageNum", required = false, defaultValue = "1") Integer pageNum) {
        Map<String, Object> map = new HashMap<>();
        map.put("date", new Date().getTime());
        map.put("bil_mark", true);
        map.put("bil_mark_r", true); //账单管理
        map.put("recordStatus", recordStatus);
        OtherUtils.ReturnValByModel(model, map);
        merchantService.getBillRecordDetailByCondition(model, date, pageNum);
        //当天账单记录下的详情（当天总入账的构成条目）
        return mDirectory + "bill_record_detail";
    }

    //门店信息
    //====店铺
    @RequestMapping(value = "/shopInfo/store", method = RequestMethod.GET)
    public String storeHomePage(Model model, RequestParams params) {
        Map<String, Object> map = new HashMap<>();
        map.put("sto_mark", true);
        map.put("sto_mark_s", true);
        map.put("date", new Date().getTime());
        OtherUtils.ReturnValByModel(model, map);
        params.setModel(model);
        merchantService.storeHomePage(params);
        return mDirectory + "shop_shopInfo";
    }

    //======================================top菜单栏url结束
}