package com.htkapp.modules.merchant.common.service.serviceImpl;

import com.github.pagehelper.PageInfo;
import com.htkapp.core.MD5Utils;
import com.htkapp.core.OtherUtils;
import com.htkapp.core.jsAjax.AjaxResponseModel;
import com.htkapp.core.params.RequestParams;
import com.htkapp.core.redisCache.JedisServiceCache;
import com.htkapp.core.shiro.common.utils.LoggerUtils;
import com.htkapp.core.shiro.core.shiro.cache.JedisManager;
import com.htkapp.core.utils.Globals;
import com.htkapp.core.utils.StringUtils;
import com.htkapp.modules.API.entity.Account;
import com.htkapp.modules.API.entity.AccountFocus;
import com.htkapp.modules.API.service.AccountFocusService;
import com.htkapp.modules.API.service.AccountServiceI;
import com.htkapp.modules.API.service.SMSBaseServiceI;
import com.htkapp.modules.admin.shopCategory.entity.ShopCategory;
import com.htkapp.modules.admin.shopCategory.service.ShopCategoryService;
import com.htkapp.modules.common.dto.IndexInfo;
import com.htkapp.modules.common.entity.LoginUser;
import com.htkapp.modules.merchant.buffetFood.entity.BuffetFoodOrder;
import com.htkapp.modules.merchant.buffetFood.entity.BuffetFoodOrderProduct;
import com.htkapp.modules.merchant.buffetFood.entity.BuffetFoodProduct;
import com.htkapp.modules.merchant.buffetFood.service.BuffetFoodOrderProductService;
import com.htkapp.modules.merchant.buffetFood.service.BuffetFoodOrderService;
import com.htkapp.modules.merchant.buffetFood.service.BuffetFoodProductService;
import com.htkapp.modules.merchant.common.dto.CommentListInfo;
import com.htkapp.modules.merchant.common.dto.TakeoutCommentList;
import com.htkapp.modules.merchant.common.entity.AccountShopNotice;
import com.htkapp.modules.merchant.common.service.AccountShopNoticeService;
import com.htkapp.modules.merchant.common.service.IndexService;
import com.htkapp.modules.merchant.common.service.MerchantService;
import com.htkapp.modules.merchant.common.service.ShopMessageCommentService;
import com.htkapp.modules.merchant.groupBuy.entity.BuyPackage;
import com.htkapp.modules.merchant.groupBuy.service.BuyPackageService;
import com.htkapp.modules.merchant.integral.entity.Integral;
import com.htkapp.modules.merchant.integral.service.IntegralService;
import com.htkapp.modules.merchant.pay.entity.*;
import com.htkapp.modules.merchant.pay.service.*;
import com.htkapp.modules.merchant.shop.entity.AccountShop;
import com.htkapp.modules.merchant.shop.entity.AccountShopReplyComments;
import com.htkapp.modules.merchant.shop.entity.RegisterApply;
import com.htkapp.modules.merchant.shop.entity.Shop;
import com.htkapp.modules.merchant.shop.service.AccountShopReplyCommentsService;
import com.htkapp.modules.merchant.shop.service.AccountShopServiceI;
import com.htkapp.modules.merchant.shop.service.RegisterApplyService;
import com.htkapp.modules.merchant.shop.service.ShopServiceI;
import com.xiaoleilu.hutool.date.BetweenFormater;
import com.xiaoleilu.hutool.date.DateUnit;
import com.xiaoleilu.hutool.date.DateUtil;
import org.apache.http.util.TextUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.interceptor.TransactionAspectSupport;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.RequestParam;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;
import java.text.DecimalFormat;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static com.xiaoleilu.hutool.date.DatePattern.NORM_DATETIME_MINUTE_PATTERN;
import static com.xiaoleilu.hutool.date.DateUtil.*;

/**
 * Created by yinqilei on 17-7-13.
 */
@Service
public class MerchantServiceImpl implements MerchantService {

    @Resource
    private AccountShopServiceI accountShopService;
    @Resource
    private SMSBaseServiceI smsService;
    @Resource
    private ShopServiceI shopService;
    @Resource
    private OrderRecordService orderRecordService;
    @Resource
    private OrderProductService orderProductService;
    @Resource
    private AccountShopNoticeService noticeService;
    @Resource
    private ShopMessageCommentService shopMessageCommentService;
    @Resource
    private OtherUtils otherUtils;
    @Resource
    private AccountShopReplyCommentsService replyCommentsService;
    @Resource
    private BillBalanceSheetService billBalanceSheetService;
    @Resource
    private BillRecordService billRecordService;
    @Resource
    private BillRecordStatisticsService billRecordStatisticsService;
    @Resource
    private BuffetFoodOrderService buffetFoodOrderService;
    @Resource
    private BuffetFoodOrderProductService buffetFoodOrderProductService;
    @Resource
    private BuffetFoodProductService buffetFoodProductService;
    @Resource
    private IntegralService integralService;
    @Resource
    private AccountFocusService accountFocusService;
    @Resource
    private AccountServiceI accountService;
    @Resource
    private JedisServiceCache serviceCache;
    @Resource
    private RegisterApplyService registerApplyService;
    @Resource
    private BuyPackageService buyPackageService;
    @Resource
    private ShopCategoryService shopCategoryService;
    @Resource
    private IndexService indexService;

    /* ===================接口开始================== */
    //异步验证商户账号是否存在
    @Override
    public AjaxResponseModel verifyMerchant(String phone) {
        if (StringUtils.isNotEmpty(phone)) {
            try {
                AccountShop accountShop = accountShopService.getAccountShopByPhoneAndUserName(phone);
                if (accountShop == null) {
                    return new AjaxResponseModel<Boolean>(Globals.COMMON_SUCCESSFUL_OPERATION, "用户未注册", false);
                } else {
                    return new AjaxResponseModel<Boolean>(Globals.COMMON_SUCCESSFUL_OPERATION, "用户已注册", true);
                }
            } catch (Exception e) {
                return new AjaxResponseModel(Globals.COMMON_OPERATION_FAILED, e.getMessage());
            }
        } else {
            return new AjaxResponseModel(Globals.COMMON_PARAMETER_ERROR);
        }
    }

    //商户注册提交
    @Override
    @Transactional
    public AjaxResponseModel submitRegisterBYyPost(AccountShop accountShop1, RequestParams params) {
        if (accountShop1 != null && params != null) {
            try {
                String valCode = smsService.findValCodeByPhone(accountShop1.getUserName());
                //调用时间计算，从注册时间开始，一个月试用期
                Date useStartDate = new Date(); //当前注册日期
                Date useEndDate = DateUtil.offsetMonth(useStartDate, 1); //到期时间
                //插入商户信息，插入商铺信息
                AccountShop accountShop = new AccountShop();
                accountShop.setUserName(accountShop1.getPhone());//用户名即手机号
                accountShop.setPassword(accountShop1.getPassword());  //密码
                accountShop.setPhone(accountShop1.getPhone()); //手机号即用户名
                accountShop.setAccountStatus(1);  //账号状态
                accountShop.setRegisterTime(format(useStartDate, NORM_DATETIME_PATTERN)); //注册时间
                accountShop.setUseStartTime(format(useStartDate, NORM_DATETIME_PATTERN));  //使用开始时间
                accountShop.setUseEndTime(useEndDate.toString());   //使用结束时间
                accountShop.setAvatarImg("upload/merchant/avatarImg/defaultMerchant.jpg"); //注册时设置默认头像
                accountShop.setToken(UUID.randomUUID().toString());
                accountShop.setSaltToken(Globals.DEFAULT_SALT_TOKEN);
                accountShop.setAccountStatus(-2);  //状态
                accountShop.setIdentity(accountShop1.getIdentity());
                accountShop.setEncryptToken(MD5Utils.getMD5Encode(accountShop.getToken() + accountShop.getSaltToken())); //md5加密后的token
                int accountShopId = accountShopService.registerAccountShopByPhone(accountShop);
                Shop shop = new Shop();
                int[] a = new int[3];
                a[0] = 0;
                a[1] = 1;
                a[2] = 2;
                RegisterApply apply = new RegisterApply();
                for (int i=0; i<a.length; i++) {
                    //(a[i] == 0 ? "外卖" : (a[i] == 1 ? "团购" : "自助点餐"))
                    shop.setShopName(params.getShopName());
                    shop.setLogoUrl("htk/upload/merchant/avatarImg/defaultMerchant.jpg");  //设置一张默认的店铺图片
                    shop.setOpeningTime("8:00-23:00");  //默认的营业时间
                    shop.setLongitude(params.getLongitude());
                    shop.setLatitude(params.getLatitude());
                    shop.setLocation(params.getLocation());
                    shop.setAddress(params.getAddress());
                    shop.setAccountShopId(accountShopId);
                    shop.setPhone(accountShop.getPhone());
                    shop.setMobilePhone(accountShop.getPhone());
                    shop.setDeliveryFee(20.00);
                    if(a[i] == 0){
                        shop.setShopCategoryId(43);
                    }else if(a[i] == 1){
                        shop.setShopCategoryId(49);
                    }
                    shop.setIntro("欢迎光临本店!!!");
                    shop.setMark(a[i]);
                    int shopId = shopService.insertShopById(shop);
                    if (accountShopId > 0 && shopId > 0) {
                        apply.setShopId(shopId);
                        apply.setAccountShopId(accountShopId);
                    } else {
                        return new AjaxResponseModel(Globals.COMMON_OPERATION_FAILED, "注册失败");
                    }
                }
                registerApplyService.insertApplyById(apply);
                return new AjaxResponseModel<String>(Globals.COMMON_SUCCESSFUL_OPERATION, "注册成功");
            } catch (Exception e) {
                TransactionAspectSupport.currentTransactionStatus().setRollbackOnly();
                return new AjaxResponseModel(Globals.COMMON_OPERATION_FAILED, e.getMessage());
            }
        } else {
            return new AjaxResponseModel(Globals.COMMON_PARAMETER_ERROR);
        }
    }

    //异步验证用户输入手机号和验证码是否正确
    @Override
    public AjaxResponseModel verifyMobilePhoneAndCode(String phone, String code) {
        if (StringUtils.isEmpty(phone) || code == null) {
            return new AjaxResponseModel(Globals.COMMON_PARAMETER_ERROR);
        } else {
            try {
                String valCode = smsService.findValCodeByPhone(phone);
                if (valCode.equals(code)) {
                    //验证成功
                    return new AjaxResponseModel(Globals.COMMON_SUCCESS_POP_WINDOW);
                } else {
                    return new AjaxResponseModel(Globals.COMMON_OPERATION_FAILED, "验证码错误");
                }
            } catch (Exception e) {
                return new AjaxResponseModel(Globals.COMMON_OPERATION_FAILED, "验证用户身份失败");
            }
        }
    }

    //找回密码确认身份成功后，更改密码接口
    @Override
    public AjaxResponseModel changePassword(String phone, String password, String code) {
        if (StringUtils.isNotEmpty(phone) && StringUtils.isNotEmpty(password) && StringUtils.isNotEmpty(code)) {
            //为了安全，再次传入验证码验证用户身份
            try {
                String valCode = smsService.findValCodeByPhone(phone);
                if (valCode.equals(code)) {
                    //相同
                    accountShopService.changePassword(phone, MD5Utils.getMD5Encode(password));
                    return new AjaxResponseModel(Globals.COMMON_SUCCESSFUL_OPERATION, "重置密码成功");
                } else {
                    return new AjaxResponseModel(Globals.COMMON_OPERATION_FAILED, "验证码错误");
                }
            } catch (Exception e) {
                return new AjaxResponseModel(Globals.COMMON_OPERATION_FAILED);
            }
        } else {
            //请求参数为空
            return new AjaxResponseModel(Globals.COMMON_PARAMETER_ERROR);
        }
    }

    //改变店铺状态接口
    @Override
    public AjaxResponseModel changeShopState(HttpServletRequest request, int stateId) {
        try {
            LoginUser user = OtherUtils.getLoginUserByRequest();
            shopService.changeShopOpenStateById(stateId, user.getUserId());
            return new AjaxResponseModel(Globals.COMMON_SUCCESSFUL_OPERATION);
        } catch (Exception e) {
            return new AjaxResponseModel(Globals.COMMON_OPERATION_FAILED);
        }
    }

    //根据用户身份和类型查找订单
    @Override
    public void getOrderListByAIdAndMark(int mark, Model model, int pageNo, String startDate, String endDate) {
        //查找订单数据，以model返到pages
        //订单类型默认为全部,订单类型默认为全部,订单排序默订是订单序号
        //选择日期默认是当前日期往前推５天
        int pageNumber = Globals.DEFAULT_PAGE_NO;
        int pageLimit = Globals.DEFAULT_PAGE_LIMIT;
        if (pageNo > 1) {
            pageNumber = pageNo;
        }
        try {
            String endDateStr = format(DateUtil.endOfDay(DateUtil.parse(endDate)), NORM_DATETIME_PATTERN);
            //查询所有已完成的订单
            List<OrderRecord> resultList = orderRecordService.getOrderPageDataByCondition(1, mark, pageNumber, pageLimit, startDate, endDateStr, 1);
            //根据订单查询订单下的商品
            if (resultList != null && resultList.size() > 0) {
                for (OrderRecord each : resultList) {
                    List<OrderProduct> lists = orderProductService.getProductListByOrderId(each.getId());
                    if (lists != null) {
                        each.setProductLists(lists);
                    }
                }
            }
            PageInfo pageInfo = new PageInfo<>(resultList);
            if (resultList != null) {
                model.addAttribute("data", resultList);
                model.addAttribute("pageInfo", pageInfo);
            }
        } catch (Exception e) {
            model.addAttribute("data", null);
            model.addAttribute("pageInfo", null);
        }
    }

    //商户首页
    @Override
    public void getHomePage(RequestParams params) {
        if (params != null) {
            try {
//                String key = "merchant-homePageMes";
//                String value = serviceCache.get(key, String.class);
                Model model = params.getModel();
//                if (value != null) {
//                    model.addAttribute("mes", value);
//                }
//                serviceCache.put(key, "商户首页");
                HttpServletRequest request = otherUtils.getRequestByMethod();
                IndexInfo indexInfo = indexService.index(request);
                model.addAttribute("data", indexInfo);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        return;
    }

    //外卖下实时订单查询
    @Override
    public void getTakeoutRealTimeOrderByCondition(Model model, int shopId, String startDate, String endDate, int statusCode) {
        try {
            List<OrderRecord> resultList = orderRecordService.getTakeoutRealTimeOrderByCondition(shopId, startDate, endDate, statusCode);
            if (resultList != null) {
                for (OrderRecord each : resultList) {
                    List<OrderProduct> productList = orderProductService.getProductListByOrderId(each.getId());
                    each.setProductLists(productList);
                }
                model.addAttribute("data", resultList);
            } else {
                model.addAttribute("data", null);
            }
            //订单概况(已接订单数，今日营业额, 待发货、配送订单数)
            DecimalFormat df = new DecimalFormat("#.00");
            double income = orderRecordService.statisticalIncomeByDate(shopId, startDate, endDate);
            int orderQuantity = orderRecordService.statisticalOrderQuantityByStateIdAndDate(shopId, 1, startDate, endDate);
            //待发货订单数
            int waitOrderQuantity = orderRecordService.statisticalQuantityByStateIdAndDate(shopId, 2, startDate, endDate);
            //待送货订单数
            int waitDeliveryOrderQuantity = orderRecordService.statisticalQuantityByStateIdAndDate(shopId, 3, startDate, endDate);
            model.addAttribute("income", Double.parseDouble(df.format(income)));
            model.addAttribute("orderQuantity", orderQuantity);
            model.addAttribute("stateCount", waitOrderQuantity + waitDeliveryOrderQuantity);
        } catch (Exception e) {
            model.addAttribute("data", null);
        }
    }

    //改变通知消息状态
    @Override
    public AjaxResponseModel changeNotificationMessageStatus(Integer id, Integer statusCode) {
        if (id != null && statusCode != null) {
            try {
                noticeService.changeNoticeStatusById(id, statusCode);
                return new AjaxResponseModel(Globals.COMMON_SUCCESSFUL_OPERATION, "成功");
            } catch (Exception e) {
                return new AjaxResponseModel(Globals.COMMON_OPERATION_FAILED);
            }
        } else {
            return new AjaxResponseModel(Globals.COMMON_PARAMETER_ERROR);
        }
    }

    //查找商户通知中心消息
    @Override
    public void getNotifyDataByToken(Model model, String token, int pageNo, int status) {
        try {
            int pageNumber = Globals.DEFAULT_PAGE_NO;
            int pageLimit = Globals.DEFAULT_PAGE_LIMIT;
            if (pageNo > 1) {
                pageNumber = pageNo;
            }
            List<AccountShopNotice> resultList = noticeService.getNoticeListByTokenAndStatus(token, status, pageNumber, pageLimit);
            model.addAttribute("data", resultList);
            PageInfo pageInfo = new PageInfo<>(resultList);
            model.addAttribute("page", pageInfo);
        } catch (Exception e) {
            model.addAttribute("data", null);
        }
    }

    //外卖评论页面列表
    @Override
    public void getTakeoutCommentListDataByToken(Model model, String accountShopToken, String starRating, String startTime, String endTime, int pageNum) {
        try {
            int pageNumber = Globals.DEFAULT_PAGE_NO;
            int pageLimit = Globals.DEFAULT_PAGE_LIMIT;
            if (pageNum > 1) {
                pageNumber = pageNum;
            }
            List<CommentListInfo> result = shopMessageCommentService.getCommentListByToken(accountShopToken, 0, 0, Integer.parseInt(starRating), pageNumber, pageLimit, startTime, endTime);
            //通过订单id查找所购卖的商品名字符串
            if (result != null) {
                for (CommentListInfo each : result) {
                    List<String> resultList = orderProductService.getOrderProductNameByOrderId(each.getOrderId());
                    StringBuffer productNameStr = new StringBuffer();
                    if (resultList == null) {
                        //查找产品名失败
                        productNameStr.append("未知商品名");
                        System.out.println("00000");
                    } else {
                        for (int i = 0; i < resultList.size(); i++) {
                            productNameStr.append(resultList.get(i));
                            if (i == resultList.size() - 1) {
                                break;
                            }
                            productNameStr.append(",");
                        }
                    }
                    each.setProductNameStr(productNameStr.toString());
                }
            }
            otherUtils.statisticalQuantity(model, accountShopToken, 0);
            double starVal = shopMessageCommentService.getStarValByStarNumDAO(accountShopToken, 0, 0);
            int starCount = shopMessageCommentService.getStarCountByStarNum(accountShopToken, 0, 0, 0);
            double averageScore = 0;
            DecimalFormat df = new DecimalFormat("0.00");
            if (starVal > 0) {
                averageScore = (starVal / starCount + starVal % starCount) / 2;
            }
            double fiveStarVal = shopMessageCommentService.getStarValByStarNumDAO(accountShopToken, 0, 5);
            double fourStarVal = shopMessageCommentService.getStarValByStarNumDAO(accountShopToken, 0, 4);
            double threeStarVal = shopMessageCommentService.getStarValByStarNumDAO(accountShopToken, 0, 3);
            double twoStarVal = shopMessageCommentService.getStarValByStarNumDAO(accountShopToken, 0, 2);
            double oneStarVal = shopMessageCommentService.getStarValByStarNumDAO(accountShopToken, 0, 1);

            double five = ((fiveStarVal / starVal) * 100);
            double four = ((fourStarVal / starVal) * 100);
            double three = ((threeStarVal / starVal) * 100);
            double two = ((twoStarVal / starVal) * 100);
            double one = ((oneStarVal / starVal) * 100);
            model.addAttribute("fiveStarPercentage", df.format(five));
            model.addAttribute("fourStarPercentage", df.format(four));
            model.addAttribute("threeStarPercentage", df.format(three));
            model.addAttribute("twoStarPercentage", df.format(two));
            model.addAttribute("oneStarPercentage", df.format(one));
            model.addAttribute("dataUpdateTime", format(new Date(), NORM_DATETIME_MINUTE_PATTERN));

            model.addAttribute("scopeVal", df.format(averageScore));
            PageInfo pageInfo = new PageInfo<>(result);
            model.addAttribute("data", result);
            model.addAttribute("page", pageInfo);
            model.addAttribute("result", result);
            //根据时间统计评论数量
            //根据星级统计评论数量
        } catch (Exception e) {
            model.addAttribute("data", null);
            model.addAttribute("page", null);
        }
    }

    //插入商户回复用户评论接口
    @Override
    public AjaxResponseModel replyMessageGiveUser(AccountShopReplyComments replyComments) {
        try {
            String accountShopToken = OtherUtils.getLoginUserByRequest().getToken();
            replyComments.setAccountShopToken(accountShopToken);
            replyCommentsService.insertReply(replyComments);
            return new AjaxResponseModel(Globals.COMMON_SUCCESSFUL_OPERATION);
        } catch (Exception e) {
            return new AjaxResponseModel(Globals.COMMON_OPERATION_FAILED);
        }
    }

    /* ===================接口结束====================== */

    /* ====================JSP接口开始===================== */
    //账单资金条件查找
    @Override
    public void getBillMoneyDataByCondition(Model model, String date, Integer type, Integer pageNum) {
        String accountShopToken = null;
        try {
            accountShopToken = OtherUtils.getLoginUserByRequest().getToken();
            int pageNumber = Globals.DEFAULT_PAGE_NO;
            int pageLimit = Globals.DEFAULT_PAGE_LIMIT;
            if (pageNum > 1) {
                pageNumber = pageNum;
            }
            //账户可用余额
            double accountBalance = billBalanceSheetService.getAccountBalance(accountShopToken);
            //待入账余额
            double waitingPostAmount = billRecordService.getAmountToBeAccountedByType(accountShopToken, 1);
            //商家的提现收款账号
            String aliPayAccount = accountShopService.selectByToken(accountShopToken).getAlipayAccount();
            String startTime = "";
            String endTime = "";
            if (OtherUtils.verifyStrIsNumber(date)) {
                //是数字
                Map<String, String> map = OtherUtils.getDateMapByNum(Integer.parseInt(date));
                startTime = map.get("startTime");
                endTime = map.get("endTime");
            } else {
                //直接是日期字符串
                //字符串不是数字,　验证字符的最后一位是否是分隔符
                String lastStr = date.substring(date.length() - 1);
                if (lastStr.equals("~")) {
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
                        ;
                    }
                } else {
                    //不包含
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
                    }
                }
            }
            List<BillBalanceSheet> resultList = billBalanceSheetService.getBalanceSheetRecordListByAccountShopToken(accountShopToken, startTime, endTime, type, pageNumber, pageLimit);
            PageInfo pageInfo = new PageInfo<>(resultList);
            DecimalFormat df = new DecimalFormat("#.00");
            model.addAttribute("accountBalance", Double.parseDouble(df.format(accountBalance + waitingPostAmount)));  //总余额
            model.addAttribute("availableBalance", Double.parseDouble(df.format(accountBalance)));  //可用余额
            model.addAttribute("remainingBalance", Double.parseDouble(df.format(waitingPostAmount)));  //待入账余额
            model.addAttribute("shroffAccountNumber", aliPayAccount);
            model.addAttribute("data", resultList);
            model.addAttribute("dateVal", date);
            model.addAttribute("page", pageInfo);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    //账单记录条件查找
    @Override
    public void getBillRecordListByCondition(Model model, String date, Integer type, Integer pageNum) {
        String accountShopToken = null;
        try {
            int pageNumber = Globals.DEFAULT_PAGE_NO;
            int pageLimit = Globals.DEFAULT_PAGE_LIMIT;
            if (pageNum > 1) {
                pageNumber = pageNum;
            }
            accountShopToken = OtherUtils.getLoginUserByRequest().getToken();
            String startTime = "";
            String endTime = "";
            if (OtherUtils.verifyStrIsNumber(date)) {
                //是数字
                Map<String, String> map = OtherUtils.getDateMapByNum(Integer.parseInt(date));
                startTime = map.get("startTime");
                endTime = map.get("endTime");
            } else {
                //直接是日期字符串
                //字符串不是数字,　验证字符的最后一位是否是分隔符
                String lastStr = date.substring(date.length() - 1);
                if (lastStr.equals("~")) {
                    System.out.println("重复方法一");
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
                    System.out.println("重复方法二");
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
                    }
                }
            }
            //根据日期、类型、分页查找账单记录
//            List<BillRecord> resultList = billRecordService.getBillRecordListByToken(accountShopToken, startTime, endTime, type, pageNumber, pageLimit);

            List<BillRecordStatistics> statisticsList = billRecordStatisticsService.getBillRecordStatisticsListByDate(accountShopToken, startTime, endTime, pageNumber, pageLimit);

            if (statisticsList != null) {
                for (BillRecordStatistics each : statisticsList) {
                    each.setGmtCreate(format(DateUtil.parse(each.getGmtCreate()), NORM_DATE_PATTERN));
                }
            }
            PageInfo pageInfo = new PageInfo<>(statisticsList);
            model.addAttribute("data", statisticsList);
            model.addAttribute("page", pageInfo);
        } catch (Exception e) {
            model.addAttribute("data", null);
        }
    }

    //帐单记录详情条件查找
    @Override
    public void getBillRecordDetailByCondition(Model model, String date, Integer pageNum) {
        try {
            //格式化日期
            String accountShopToken = OtherUtils.getLoginUserByRequest().getToken();
            Date curDate = DateUtil.parse(date);
            String startTime = format(DateUtil.beginOfDay(curDate), NORM_DATETIME_PATTERN);
            String endTime = format(DateUtil.endOfDay(curDate), NORM_DATETIME_PATTERN);
            Double todayIncome = billRecordService.getTodayIncomeByDate(accountShopToken, startTime, endTime);
            Double orderIncome = billRecordService.getTodayOrderIncomeByDate(accountShopToken, startTime, endTime);
            Double spendingOnOrder = billRecordService.getSpendingOnOrderByDate(accountShopToken, startTime, endTime);
            List<BillRecord> billRecordList = billRecordService.getBillRecordListByDate(accountShopToken, startTime, endTime);
            model.addAttribute("queryDate", date);
            model.addAttribute("data", billRecordList);
            model.addAttribute("todayIncome", todayIncome);
            model.addAttribute("orderIncome", orderIncome);
            model.addAttribute("spendingOnOrder", spendingOnOrder);
        } catch (Exception e) {
            model.addAttribute("data", null);
            model.addAttribute("todayIncome", null);
            model.addAttribute("orderIncome", null);
            model.addAttribute("spendingOnOrder", null);
        }
    }

    //自助点餐订单查询未结算订单
    @Override
    public void getBuffetFoodOrderQueryResult(Model model, Integer pageNum) {
        //根据商铺Token查找未结算订单列表
        int pageNumber = Globals.DEFAULT_PAGE_NO;
        int pageLimit = Globals.DEFAULT_PAGE_LIMIT;
        try {
            String token = OtherUtils.getLoginUserByRequest().getToken();
            int accountShopId = OtherUtils.getLoginUserByRequest().getUserId();
            List<BuffetFoodOrder> orderList = buffetFoodOrderService.getBuffetFoodOrderListByToken(token, null, null, 1, pageNumber, pageLimit);
            if (orderList != null) {
                int quantity = 0;
                for (BuffetFoodOrder each : orderList) {
                    quantity = 0;
                    //遍历订单列表，根据订单查询订单中的商品
                    List<BuffetFoodOrderProduct> orderProductList = buffetFoodOrderProductService.getOrderProductListById(each.getId());
                    each.setProductLists(orderProductList);
                    Date date = new Date();
                    String commitMinute = "";
                    long minute = DateUtil.between(DateUtil.parse(each.getOrderTime()), date, DateUnit.DAY);
                    long time = date.getTime() - DateUtil.parse(each.getOrderTime()).getTime();
                    commitMinute = DateUtil.formatBetween(time, BetweenFormater.Level.MINUTE);
                    each.setMinute(commitMinute);
                    if (orderProductList != null) {
                        for (BuffetFoodOrderProduct every : orderProductList) {
                            quantity += every.getQuantity();
                        }
                    }
                    each.setSum(quantity);
                }
            }
            PageInfo pageInfo = new PageInfo<>(orderList);
            //查询商铺下的自助点餐商品
            List<BuffetFoodProduct> productList = buffetFoodProductService.getBuffetFoodProductListByAccountShopId(accountShopId);
            //订单提交的时间计算
            //orderList　结果集为null　返回由EL表达式判断,为空则显示没有数据的布局
            model.addAttribute("data", orderList);
            model.addAttribute("page", pageInfo);
            model.addAttribute("productData", productList);
        } catch (Exception e) {
            model.addAttribute("data", null);
        }
    }

    //自助点餐订单查询已结算订单
    @Override
    public void getBuffetFoodOrderDoneResult(Model model, String startTime, String endTime, Integer pageNum) {
        //根据商铺Token查找未结算订单列表
        int pageNumber = Globals.DEFAULT_PAGE_NO;
        int pageLimit = Globals.DEFAULT_PAGE_LIMIT;
        try {
            String token = OtherUtils.getLoginUserByRequest().getToken();
            List<BuffetFoodOrder> orderList = buffetFoodOrderService.getBuffetFoodOrderListByToken(token, startTime, endTime, 2, pageNumber, pageLimit);
            if (orderList != null) {
                int quantity = 0;
                for (BuffetFoodOrder each : orderList) {
                    quantity = 0;
                    //遍历订单列表，根据订单查询订单中的商品
                    List<BuffetFoodOrderProduct> orderProductList = buffetFoodOrderProductService.getOrderProductListById(each.getId());
                    each.setProductLists(orderProductList);
                    Date date = new Date();
                    long time = date.getTime() - DateUtil.parse(each.getOrderTime()).getTime();
                    String commitMinute = DateUtil.formatBetween(time, BetweenFormater.Level.MINUTE);
                    each.setMinute(commitMinute);
                    if (orderProductList != null) {
                        for (BuffetFoodOrderProduct every : orderProductList) {
                            quantity += every.getQuantity();
                        }
                    }
                    each.setSum(quantity);
                }
            }
            //orderList　结果集为null　返回由EL表达式判断,为空则显示没有数据的布局
            model.addAttribute("data", orderList);
        } catch (Exception e) {
            model.addAttribute("data", null);
        }
    }

    //团购评论页面列表
    @Override
    public void getGroupBuyCommentListDataByToken(Model model, String accountShopToken, String starRating, String startTime, String endTime, int pageNum) {
        try {
            int pageNumber = Globals.DEFAULT_PAGE_NO;
            int pageLimit = Globals.DEFAULT_PAGE_LIMIT;
            if (pageNum > 1) {
                pageNumber = pageNum;
            }
            List<CommentListInfo> result = shopMessageCommentService.getCommentListByToken(accountShopToken, 1, 0, Integer.parseInt(starRating), pageNumber, pageLimit, startTime, endTime);
            //通过订单id查找所购卖的商品名字符串
            if (result != null) {
                for (CommentListInfo each : result) {
                    List<String> resultList = orderProductService.getOrderProductNameByOrderId(each.getOrderId());
                    StringBuffer productNameStr = new StringBuffer();
                    if (resultList == null) {
                        //查找产品名失败
                        productNameStr.append("未知商品名");
                        System.out.println("11111");
                    } else {
                        for (int i = 0; i < resultList.size(); i++) {
                            productNameStr.append(resultList.get(i));
                            if (i == resultList.size() - 1) {
                                break;
                            }
                            productNameStr.append(",");
                        }
                    }
                    each.setProductNameStr(productNameStr.toString());
                }
            }
            otherUtils.statisticalQuantity(model, accountShopToken, 1);
            double starVal = shopMessageCommentService.getStarValByStarNumDAO(accountShopToken, 1, 0);
            int starCount = shopMessageCommentService.getStarCountByStarNum(accountShopToken, 1, 0, 0);
            double averageScore = 0;
            DecimalFormat df = new DecimalFormat("0.00");
            if (starVal > 0) {
                averageScore = (starVal / starCount + starVal % starCount) / 2;
            }
            double fiveStarVal = shopMessageCommentService.getStarValByStarNumDAO(accountShopToken, 1, 5);
            double fourStarVal = shopMessageCommentService.getStarValByStarNumDAO(accountShopToken, 1, 4);
            double threeStarVal = shopMessageCommentService.getStarValByStarNumDAO(accountShopToken, 1, 3);
            double twoStarVal = shopMessageCommentService.getStarValByStarNumDAO(accountShopToken, 1, 2);
            double oneStarVal = shopMessageCommentService.getStarValByStarNumDAO(accountShopToken, 1, 1);

            double five = ((fiveStarVal / starVal) * 100);
            double four = ((fourStarVal / starVal) * 100);
            double three = ((threeStarVal / starVal) * 100);
            double two = ((twoStarVal / starVal) * 100);
            double one = ((oneStarVal / starVal) * 100);
            model.addAttribute("fiveStarPercentage", df.format(five));
            model.addAttribute("fourStarPercentage", df.format(four));
            model.addAttribute("threeStarPercentage", df.format(three));
            model.addAttribute("twoStarPercentage", df.format(two));
            model.addAttribute("oneStarPercentage", df.format(one));
            model.addAttribute("dataUpdateTime", format(new Date(), NORM_DATETIME_MINUTE_PATTERN));
            model.addAttribute("scopeVal", df.format(averageScore));
            PageInfo pageInfo = new PageInfo<>(result);
            model.addAttribute("data", result);
            model.addAttribute("page", pageInfo);
            model.addAttribute("result", result);
            //根据时间统计评论数量
            //根据星级统计评论数量
        } catch (Exception e) {
            model.addAttribute("data", null);
            model.addAttribute("page", null);
        }
    }

    //自助点餐评论页面列表
    @Override
    public void getBuffetFoodCommentListDataByToken(Model model, String accountShopToken, String starRating, String startTime, String endTime, int pageNum) {
        try {
            int pageNumber = Globals.DEFAULT_PAGE_NO;
            int pageLimit = Globals.DEFAULT_PAGE_LIMIT;
            if (pageNum > 1) {
                pageNumber = pageNum;
            }
            List<CommentListInfo> result = shopMessageCommentService.getCommentListByToken(accountShopToken, 2, 0, Integer.parseInt(starRating), pageNumber, pageLimit, startTime, endTime);
            //通过订单id查找所购卖的商品名字符串
            if (result != null) {
                for (CommentListInfo each : result) {
                    List<String> resultList = orderProductService.getOrderProductNameByOrderId(each.getOrderId());
                    StringBuffer productNameStr = new StringBuffer();
                    if (resultList == null) {
                        //查找产品名失败
                        productNameStr.append("未知商品名");
                    } else {
                        for (int i = 0; i < resultList.size(); i++) {
                            productNameStr.append(resultList.get(i));
                            if (i == resultList.size() - 1) {
                                break;
                            }
                            productNameStr.append(",");
                        }
                    }
                    each.setProductNameStr(productNameStr.toString());
                }
            }
            otherUtils.statisticalQuantity(model, accountShopToken, 2);
            double starVal = shopMessageCommentService.getStarValByStarNumDAO(accountShopToken, 2, 0);
            int starCount = shopMessageCommentService.getStarCountByStarNum(accountShopToken, 2, 0, 0);
            double averageScore = 0;
            DecimalFormat df = new DecimalFormat("0.0");
            if (starVal > 0) {
                averageScore = (starVal / starCount + starVal % starCount) / 2;
            }
            double fiveStarVal = shopMessageCommentService.getStarValByStarNumDAO(accountShopToken, 2, 5);
            double fourStarVal = shopMessageCommentService.getStarValByStarNumDAO(accountShopToken, 2, 4);
            double threeStarVal = shopMessageCommentService.getStarValByStarNumDAO(accountShopToken, 2, 3);
            double twoStarVal = shopMessageCommentService.getStarValByStarNumDAO(accountShopToken, 2, 2);
            double oneStarVal = shopMessageCommentService.getStarValByStarNumDAO(accountShopToken, 2, 1);

            double five = ((fiveStarVal / starVal) * 100);
            double four = ((fourStarVal / starVal) * 100);
            double three = ((threeStarVal / starVal) * 100);
            double two = ((twoStarVal / starVal) * 100);
            double one = ((oneStarVal / starVal) * 100);
            model.addAttribute("fiveStarPercentage", df.format(five));
            model.addAttribute("fourStarPercentage", df.format(four));
            model.addAttribute("threeStarPercentage", df.format(three));
            model.addAttribute("twoStarPercentage", df.format(two));
            model.addAttribute("oneStarPercentage", df.format(one));
            model.addAttribute("dataUpdateTime", format(new Date(), NORM_DATETIME_MINUTE_PATTERN));

            model.addAttribute("scopeVal", df.format(averageScore));
            PageInfo pageInfo = new PageInfo<>(result);
            model.addAttribute("data", result);
            model.addAttribute("page", pageInfo);
            model.addAttribute("result", result);
            //根据时间统计评论数量
            //根据星级统计评论数量
        } catch (Exception e) {
            model.addAttribute("data", null);
            model.addAttribute("page", null);
        }
    }

    //积分列表
    @Override
    public void integralListDateAndByCondition(Model model, Integer pageNum, String userPhone) {
        //根据当前登陆商户查找用户积分列表
        //根据用户手机号筛选用户
        //1.根据用户手机号查找用户是否收藏当前店铺，如果已收藏,再查找积分表
        //如果没有收藏，则返回没有
        try {
            String accountShopToken = OtherUtils.getLoginUserByRequest().getToken();
            int pageNumber = Globals.DEFAULT_PAGE_NO;
            int pageLimit = Globals.DEFAULT_PAGE_LIMIT;
            if (pageNum > 1) {
                pageNumber = pageNum;
            }
            String condition = null;
            if (StringUtils.isNotEmpty(userPhone)) {
                //根据当前手机号是否收藏当前店铺
                AccountFocus accountFocus = accountFocusService.getAccountFocusByUserPhone(userPhone, accountShopToken);
                if (accountFocus != null) {
                    condition = accountFocus.getAccountToken();
                } else {
                    model.addAttribute("data", null);
                    model.addAttribute("userPhone", userPhone);
                    return;
                }
            }
            List<Integral> integralList = integralService.getIntegralUserListByShopToken(accountShopToken, condition, pageNumber, pageLimit);
            if (integralList != null) {
                for (Integral each : integralList) {
                    if (each.getJoinTime() != null) {
                        each.setJoinTime(format(DateUtil.parseDate(each.getJoinTime()), NORM_DATE_PATTERN));
                    }
                    Account account = accountService.selectByToken(each.getAccountToken());
                    if (account != null) {
                        each.setUserPhone(account.getUserName());
                    }
                    if (each.getLastUseTime() != null) {
                        each.setLastUseTime(format(DateUtil.parseDate(each.getJoinTime()), NORM_DATE_PATTERN));
                    }
                    //查次根据用户token查看积分
                    Integer val = integralService.getIntegralValByAccountToken(accountShopToken, each.getAccountToken());
                    each.setVal(val);
                }
                model.addAttribute("data", integralList);
            }
        } catch (Exception e) {
            model.addAttribute("data", null);
            e.printStackTrace();
        }
    }

    //团购商品页面
    @Override
    public void groupBuyProductIndex(RequestParams params) {
        if (params != null && params.getModel() != null) {
            try {
                //查询店铺下的团购商品显示
                LoginUser user = OtherUtils.getLoginUserByRequest();
                Shop shop = shopService.getShopDataByAccountShopIdAndMark(user.getUserId(), 1);
                if (shop != null) {
                    List<BuyPackage> resultList = buyPackageService.getGroupBuyListById(shop.getShopId());
                    if (resultList != null) {
                        for (BuyPackage each : resultList) {
                            each.setImgUrl(OtherUtils.getRootDirectory() + each.getImgUrl());
                        }
                        PageInfo pageInfo = new PageInfo<>(resultList);
                        Model model = params.getModel();
                        model.addAttribute("data", resultList);
                        model.addAttribute("pageInfo", pageInfo);
                    }
                }
            } catch (Exception e) {
                return;
            }
        }
    }

    //自助点餐新订单页面
    @Override
    public void buffetFoodNewOrder(RequestParams params) {
        if (params != null && params.getModel() != null) {
            Model model = params.getModel();
            int pageNumber = Globals.DEFAULT_PAGE_NO;
            int pageLimit = Globals.DEFAULT_PAGE_LIMIT;
            if (params.getPageNum() > 1) {
                pageNumber = params.getPageNum();
            }
            try {
                String orderDesc = "gmt_create desc";
                LoginUser user = OtherUtils.getLoginUserByRequest();
                Shop shop = shopService.getShopDataByAccountShopIdAndMark(user.getUserId(), 0);
                if (shop != null) {
                    List<BuffetFoodOrder> resultList = buffetFoodOrderService.getBuffetFoodOrderListByShopIdAndOrderStateId(orderDesc, shop.getShopId(), 1, pageNumber, pageLimit);
                    if (resultList != null) {
                        int quantity = 0;
                        //计算总数量
                        for (BuffetFoodOrder each : resultList) {
                            quantity = 0;
                            List<BuffetFoodOrderProduct> orderProductList = buffetFoodOrderProductService.getOrderProductListById(each.getId());
                            System.out.println("新订单");
                            each.setProductLists(orderProductList);
                            Date date = new Date();
                            long time = date.getTime() - DateUtil.parse(each.getOrderTime()).getTime();
                            String commitMinute = DateUtil.formatBetween(time, BetweenFormater.Level.MINUTE);
                            each.setMinute(commitMinute);
                            if (orderProductList != null) {
                                for (BuffetFoodOrderProduct every : orderProductList) {
                                    quantity += every.getQuantity();
                                }
                            }
                            each.setSum(quantity);
                        }
                    }
                    model.addAttribute("data", resultList);
                }
                return;
            } catch (Exception e) {
                model.addAttribute("data", null);
            }
        } else {
            LoggerUtils.fmtDebug(getClass(), "params is null");
            return;
        }
    }

    //自助点餐订单处理(订单调整)
    @Override
    public void buffetFoodOrderEdit(RequestParams params) {
        if (params != null) {
            try {
                Model model = params.getModel();
                LoginUser user = OtherUtils.getLoginUserByRequest();
                Shop shop = shopService.getShopByAccountShopIdAndMark(user.getUserId(), 0);
                int pageNumber = Globals.DEFAULT_PAGE_NO;
                int pageLimit = Globals.DEFAULT_PAGE_LIMIT;
                if (params.getPageNum() > 1) {
                    pageNumber = params.getPageNum();
                }
                String orderDesc = "gmt_create desc";
                List<BuffetFoodOrder> result = buffetFoodOrderService.getAdjustOrderList(shop.getShopId(), orderDesc, pageNumber, pageLimit);
                if (result != null) {
                    int quantity = 0;
                    for (BuffetFoodOrder each : result) {
                        quantity = 0;
                        List<BuffetFoodOrderProduct> orderProductList = buffetFoodOrderProductService.getOrderProductListById(each.getId());
                        each.setProductLists(orderProductList);
                        System.out.println("自助新订单");
                        Date date = new Date();
                        long time = date.getTime() - DateUtil.parse(each.getOrderTime()).getTime();
                        String commitMinute = DateUtil.formatBetween(time, BetweenFormater.Level.MINUTE);
                        each.setMinute(commitMinute);
                        if (orderProductList != null) {
                            for (BuffetFoodOrderProduct every : orderProductList) {
                                quantity += every.getQuantity();
                            }
                        }
                        each.setSum(quantity);
                    }
                }
                model.addAttribute("data", result);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    //自助点餐订单处理(催单)
    @Override
    public void buffetFoodOrderReminder(RequestParams params) {
        if (params != null) {
            try {
                Model model = params.getModel();
                LoginUser user = OtherUtils.getLoginUserByRequest();
                Shop shop = shopService.getShopByAccountShopIdAndMark(user.getUserId(), 0);
                int pageNumber = Globals.DEFAULT_PAGE_NO;
                int pageLimit = Globals.DEFAULT_PAGE_LIMIT;
                if (params.getPageNum() > 1) {
                    pageNumber = params.getPageNum();
                }
                String orderDesc = "gmt_create desc";
                List<BuffetFoodOrder> result = buffetFoodOrderService.getReminderOrderList(shop.getShopId(), orderDesc, pageNumber, pageLimit);
                if (result != null) {
                    for (BuffetFoodOrder each : result) {
                        List<BuffetFoodOrderProduct> orderProductList = buffetFoodOrderProductService.getOrderProductListById(each.getId());
                        System.out.println("新订单");
                        each.setProductLists(orderProductList);
                        Date date = new Date();
                        long time = date.getTime() - DateUtil.parse(each.getOrderTime()).getTime();
                        String commitMinute = DateUtil.formatBetween(time, BetweenFormater.Level.MINUTE);
                        each.setMinute(commitMinute);
                    }
                }
                model.addAttribute("data", result);
            } catch (Exception x) {
                return;
            }
        }
    }

    //门店信息
    @Override
    public void storeHomePage(RequestParams params) {
        //查询店铺信息
        if (params != null) {
            //查询店铺信息
            try {
                LoginUser user = OtherUtils.getLoginUserByRequest();
                AccountShop accountShop = accountShopService.getAccountShopDataById(user.getUserId());
                Shop shop = shopService.getShopMessageById(user.getUserId(), 0);
                Model model = params.getModel();
                if (shop != null) {
                    shop.setLogoUrl(OtherUtils.getRootDirectory() + shop.getLogoUrl());
                    String existedQrUrl = shop.getShopQrCodeUrl();
                    if (TextUtils.isEmpty(existedQrUrl)) {
                        String qrImgURL = OtherUtils.getImgUrl(String.valueOf(shop.getShopId()), "/shop/QRCode/", 1);
                        if (qrImgURL != null) {
                            shop.setBufImg(OtherUtils.getRootDirectory() + qrImgURL);
                            shopService.updateShopQRCode(qrImgURL,shop.getShopId());
                        }
                    }
                    shop.setShopQrCodeUrl(OtherUtils.getRootDirectory() + shop.getShopQrCodeUrl());
                    //查询店铺分类和父分类
                    StringBuffer stringBuffer = new StringBuffer();
                    ShopCategory shopCategory = shopCategoryService.getCategoryById(shop.getShopCategoryId());
                    if (shopCategory != null && shopCategory.getParentId() != 0) {
                        stringBuffer.append(shopCategory.getCategoryName());
                        ShopCategory sc = shopCategoryService.getCategoryById(shopCategory.getParentId());
                        if (sc != null) {
                            stringBuffer.append(";").append(sc.getCategoryName());
                            shop.setShopCategoryName(stringBuffer.toString());
                        }
                    }
                    //查询当前店铺下是否有自助点餐商铺
                    Shop shop1 = shopService.getShopByAccountShopIdAndMark(accountShop.getId(), 2);
                    if (shop1 != null) {
                        if (shop1.getShopQrCodeUrl() == null) {
                            //生成自助点餐二维码
                            String qrImgURL = OtherUtils.getImgUrl(String.valueOf(shop1.getShopId()), "/shop/QRCode/", 1);
                            if (qrImgURL != null) {
                                shop.setBufImg(OtherUtils.getRootDirectory() + qrImgURL);
                                shopService.updateShopQRCode(qrImgURL,shop1.getShopId());
                            }
                        }
                    }
                    shop.setBufImg(OtherUtils.getRootDirectory() + shop1.getShopQrCodeUrl());
                    //商户使用剩余时间
                    String useRemainingTime = DateUtil.formatBetween(new Date(), DateUtil.parse(accountShop.getUseEndTime()), BetweenFormater.Level.MINUTE);
                    model.addAttribute("data", shop);
                    model.addAttribute("useRemainingTime", useRemainingTime);
                }
            } catch (Exception e) {
                return;
            }
        }
    }

    /* ===================JSP接口结束======================= */
}