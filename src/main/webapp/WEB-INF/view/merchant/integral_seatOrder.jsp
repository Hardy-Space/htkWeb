<%--
  Created by IntelliJ IDEA.
  User: terabithia
  Date: 12/31/17
  Time: 8:41 AM
  To change this template use File | Settings | File Templates.
--%>
<%@ page contentType="text/html;charset=UTF-8" language="java"
         pageEncoding="UTF-8" %>
<%@ include file="/WEB-INF/view/common/url.jsp" %>
<!DOCTYPE HTML>
<%@include file="IE_lang.jsp" %>
<head>
    <title>订座管理</title>
    <meta charset="utf-8"/>
    <meta name="renderer" content="webkit">
    <meta http-equiv="X-UA-Compatible" content="IE=edge,chrome=1">
    <meta name="viewport" content="width=device-width, initial-scale=1, maximum-scale=1">
    <%@include file="head.jsp" %>
    <style>
        .phoneSearch > .condition > label {
            float: left;
            font-size: 15px;
            font-weight: 600;
            width: 80px;
            line-height: 30px;
        }

        .phoneSearch > .condition > div.block-input > div.inputDiv > input {
            width: 100%;
            height: 30px;
            border-radius: 2px;
            border: 1px solid #e2e2e2;
            padding-left: 10px;
            font-size: 15px;
        }

        .phoneSearch > .condition > div.block-input > div.buttonDiv > button {
            width: 50%;
            height: 30px;
            border-radius: 2px;
            border: 1px solid #1E9FFF;
            background-color: #1E9FFF;
            color: #fff;
            margin-left: 20px;
        }

        .block-input > div {
            display: inline-block;
        }

        .block-input > div.inputDiv {
            width: 15%;
        }

        .block-input > div.buttonDiv {
            width: 10%;
        }

        a {
            color: #337ab7;
        }
    </style>

    <style>

        .childModel > div.formShowItem > label {
            float: left;
            font-size: 14px;
            font-weight: 600;
            line-height: 30px;
            width: 50px;
            text-align: right;
        }

        .childModel > div.formShowItem >div.input-block > input {
            margin-left: 10px;
            border-radius: 2px;
            height: 30px;
            border: 1px solid #e2e2e2;
            padding-left: 5px;
            width: 50%;
        }

        .childModel > div.formShowItem > div.input-bloc {
            padding-left: 45px;
        }

        .content {
            background-color: white;
            padding: 10px;
        }
    </style>
</head>
<body>
<div class="layui-layout layui-layout-admin">
    <%@include file="top.jsp" %>
    <div class="layui-body" style="background-color: #f3f3f4">
        <div class="body-content">
            <div class="body-content">
                <div class="content">
                    <div>
                        <table class="layui-table">
                            <colgroup>
                                <col width="150">
                                <col>
                            </colgroup>
                            <thead>
                            <tr>
                                <th>序号</th>
                                <th>座位名</th>
                                <th>状态</th>
                                <th>操作</th>
                            </tr>
                            </thead>
                            <tbody>
                            <tr>
                                <td>#1</td>
                                <td>Ａ区1座</td>
                                <td>已有人</td>
                                <td>修改</td>
                            </tr>
                            <tr>
                                <td>#1</td>
                                <td>Ａ区1座</td>
                                <td>已有人</td>
                                <td>修改</td>
                            </tr>
                            <tr>
                                <td>#1</td>
                                <td>Ａ区1座</td>
                                <td>已有人</td>
                                <td>修改</td>
                            </tr>
                            <tr>
                                <td>#1</td>
                                <td>Ａ区1座</td>
                                <td>已有人</td>
                                <td>修改</td>
                            </tr>
                            </tbody>
                        </table>
                    </div>
                    <div>
                        <div id="page"></div>
                    </div>
                </div>
            </div>
        </div>
    </div>
    <%@include file="footer.jsp" %>
</div>
</body>
<%@include file="js.jsp" %>
<script type="text/javascript">
    layui.use(['element', 'util', 'layer', 'laydate', 'laypage'], function () {
        var element = layui.element; //导航的hover效果、二级菜单等功能，需要依赖element模块
        var page = layui.laypage;

        //不显示首页尾页
        page.render({
            elem: 'page'
            ,count: 10
            ,theme: '#1E9FFF'
            ,first: false
            ,last: false
        });

    });
</script>
</html>
