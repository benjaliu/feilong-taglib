/*
 * Copyright (C) 2008 feilong
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *         http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.feilong.taglib.display.breadcrumb;

import java.net.URL;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.feilong.core.net.URIUtil;
import com.feilong.core.net.URLUtil;
import com.feilong.core.tools.jsonlib.JsonUtil;
import com.feilong.core.util.CollectionsUtil;
import com.feilong.core.util.Validator;
import com.feilong.taglib.display.breadcrumb.command.BreadCrumbEntity;
import com.feilong.taglib.display.breadcrumb.command.BreadCrumbParams;
import com.feilong.taglib.display.breadcrumb.command.BreadCrumbVMParams;
import com.feilong.tools.velocity.VelocityUtil;

/**
 * 面包屑渲染核心工具类.
 * 
 * <h3>关于 {@code currentPath} 逻辑:</h3>
 * 
 * <blockquote>
 * 在一堆 {@code List<BreadCrumbEntity<Object>>}中 ,
 * <ul>
 * <li>如果设置了 {@link BreadCrumbParams#setCurrentPath(String)}参数,那么{@code currentPath} 路径,然后渲染到当前节点;</li>
 * <li>如果设置了{@link BreadCrumbParams#setCurrentPath(String)}参数,如果没有找到{@code currentPath},什么都不会渲染;因为{@code currentPath}不在所有的面包屑中</li>
 * <li>如果没有传递{@code currentPath},那么渲染全部的{@code List<BreadCrumbEntity<Object>>},但是如果此时的{@code List<BreadCrumbEntity<Object>>}
 * 不是标准的面包屑树,即如果含有重复的parentId,那么会 throw {@link IllegalArgumentException}
 * </ul>
 * </blockquote>
 * 
 * <h3>关于 {@code BreadCrumbParams#getUrlPrefix()} 逻辑:</h3>
 * 
 * <blockquote>
 * <p>
 * 如果 {@link BreadCrumbEntity#getPath()} 是绝对路径,那么是不会拼接{@code BreadCrumbParams#getUrlPrefix()}的,<br>
 * 如果 {@link BreadCrumbEntity#getPath()} 不是绝对路径,那么会调用 {@link URIUtil#getUnionUrl(URL, String)} 进行union
 * </p>
 * </blockquote>
 *
 * @author feilong
 * @version 1.2.2 2015年7月16日 下午2:08:09
 * @since 1.2.2
 */
public class BreadCrumbUtil{

    /** The Constant LOGGER. */
    private static final Logger       LOGGER            = LoggerFactory.getLogger(BreadCrumbUtil.class);

    /** The Constant VELOCITY_UTIL. */
    private static final VelocityUtil VELOCITY_UTIL     = new VelocityUtil();

    /** The Constant VM_KEY_BREADCRUMB. */
    private static final String       VM_KEY_BREADCRUMB = "breadCrumbVMParams";

    /**
     * 获得 bread crumb content.
     *
     * @param breadCrumbParams
     *            the bread crumb params
     * @return <ul>
     *         <li>if Validator.isNullOrEmpty(breadCrumbParams) , throw {@link NullPointerException}</li>
     *         <li>if Validator.isNullOrEmpty(breadCrumbEntityList) , throw {@link NullPointerException}</li>
     *         <li>if Validator.isNullOrEmpty(currentBreadCrumbEntityTreeList) , throw {@link StringUtils#EMPTY}</li>
     *         </ul>
     */
    public static String getBreadCrumbContent(BreadCrumbParams breadCrumbParams){
        if (Validator.isNullOrEmpty(breadCrumbParams)){
            throw new NullPointerException("the breadCrumbParams is null or empty!");
        }
        List<BreadCrumbEntity<Object>> breadCrumbEntityList = breadCrumbParams.getBreadCrumbEntityList();
        if (Validator.isNullOrEmpty(breadCrumbEntityList)){
            throw new NullPointerException("breadCrumbEntityList is null or empty!");
        }

        if (LOGGER.isDebugEnabled()){
            LOGGER.debug("input breadCrumbParams info:[{}]", JsonUtil.format(breadCrumbParams));
        }

        //***************************************************************************************
        List<BreadCrumbEntity<Object>> currentBreadCrumbEntityTreeList = lookUpCurrentBreadCrumbEntityTreeList(breadCrumbParams);

        if (Validator.isNullOrEmpty(currentBreadCrumbEntityTreeList)){
            return StringUtils.EMPTY;
        }

        //重构path地址
        currentBreadCrumbEntityTreeList = restructureBreadCrumbEntityTreeListPath(
                        currentBreadCrumbEntityTreeList,
                        breadCrumbParams.getUrlPrefix());

        //***************************************************************************************

        BreadCrumbVMParams<Object> breadCrumbVMParams = new BreadCrumbVMParams<Object>();
        breadCrumbVMParams.setBreadCrumbEntityList(currentBreadCrumbEntityTreeList);
        breadCrumbVMParams.setConnector(breadCrumbParams.getConnector());

        //***************************************************************************************
        Map<String, Object> contextKeyValues = new HashMap<String, Object>();
        contextKeyValues.put(VM_KEY_BREADCRUMB, breadCrumbVMParams);

        String siteMapString = VELOCITY_UTIL.parseTemplateWithClasspathResourceLoader(breadCrumbParams.getVmPath(), contextKeyValues);
        LOGGER.debug("siteMapString is:[{}]", siteMapString);
        return siteMapString;
    }

    /**
     * Restructure bread crumb entity tree list path.
     *
     * @param currentBreadCrumbEntityTreeList
     *            the current bread crumb entity tree list
     * @param urlPrefix
     *            the url prefix
     * @return the list< bread crumb entity< object>>
     * @since 1.2.2
     */
    private static List<BreadCrumbEntity<Object>> restructureBreadCrumbEntityTreeListPath(
                    List<BreadCrumbEntity<Object>> currentBreadCrumbEntityTreeList,
                    String urlPrefix){
        if (Validator.isNullOrEmpty(urlPrefix)){
            return currentBreadCrumbEntityTreeList;
        }else{
            for (BreadCrumbEntity<Object> breadCrumbEntity : currentBreadCrumbEntityTreeList){
                String path = breadCrumbEntity.getPath();

                if (URIUtil.isAbsolutePath(path)){
                    //nothing to do 
                }else{
                    URL context = URLUtil.newURL(urlPrefix);
                    String unionUrl = URIUtil.getUnionUrl(context, path);
                    breadCrumbEntity.setPath(unionUrl);
                }
            }
        }
        return currentBreadCrumbEntityTreeList;
    }

    /**
     * 按照父子关系排序好的 list.
     *
     * @param <PK>
     *            the generic type
     * @param breadCrumbParams
     *            the bread crumb params
     * @return the all parent site map entity list
     */
    private static <PK> List<BreadCrumbEntity<PK>> lookUpCurrentBreadCrumbEntityTreeList(BreadCrumbParams breadCrumbParams){
        String currentPath = breadCrumbParams.getCurrentPath();
        List<BreadCrumbEntity<PK>> breadCrumbEntityList = breadCrumbParams.getBreadCrumbEntityList();

        //如果没有传递{@code currentPath},那么渲染全部的{@code List<BreadCrumbEntity<Object>>},
        //但是如果此时的{@code List<BreadCrumbEntity<Object>>} 不是标准的面包屑树,即如果含有重复的parentId,那么会 throw {@link IllegalArgumentException}
        if (Validator.isNullOrEmpty(currentPath)){
            //find all
            Map<PK, Integer> groupCount = CollectionsUtil.groupCount(breadCrumbEntityList, "parentId");
            for (Map.Entry<PK, Integer> entry : groupCount.entrySet()){
                Integer value = entry.getValue();
                if (value > 1){
                    throw new IllegalArgumentException("currentPath isNullOrEmpty,but breadCrumbEntityList has repeat parentId data!");
                }
            }
            return sortOutAllParentBreadCrumbEntityList(breadCrumbEntityList);
        }
        BreadCrumbEntity<PK> currentBreadCrumbEntity = getBreadCrumbEntityByPath(currentPath, breadCrumbEntityList);

        //如果设置了{@link BreadCrumbParams#setCurrentPath(String)}参数,如果没有找到{@code currentPath},什么都不会渲染;因为{@code currentPath}不在所有的面包屑中
        if (Validator.isNullOrEmpty(currentBreadCrumbEntity)){
            LOGGER.warn("when currentPath is:{},in breadCrumbEntityList,can not find", currentPath, JsonUtil.format(breadCrumbParams));
            return null;
        }

        //如果设置了 {@link BreadCrumbParams#setCurrentPath(String)}参数,那么{@code currentPath} 路径,然后渲染到当前节点;
        return sortOutAllParentBreadCrumbEntityList(currentBreadCrumbEntity, breadCrumbEntityList);
    }

    /**
     * 按照父子关系排序好的 list.
     *
     * @param <PK>
     *            the generic type
     * @param breadCrumbEntityList
     *            the bread crumb entity list
     * @return the all parent bread crumb entity list
     */
    private static <PK> List<BreadCrumbEntity<PK>> sortOutAllParentBreadCrumbEntityList(List<BreadCrumbEntity<PK>> breadCrumbEntityList){
        BreadCrumbEntity<PK> currentBreadCrumbEntity = null;
        return sortOutAllParentBreadCrumbEntityList(currentBreadCrumbEntity, breadCrumbEntityList);
    }

    //*******************************************************************

    /**
     * 按照父子关系排序好的 list.
     *
     * @param <PK>
     *            the generic type
     * @param currentBreadCrumbEntity
     *            the current bread crumb entity
     * @param breadCrumbEntityList
     *            the bread crumb entity list
     * @return the all parent bread crumb entity list
     */
    private static <PK> List<BreadCrumbEntity<PK>> sortOutAllParentBreadCrumbEntityList(
                    BreadCrumbEntity<PK> currentBreadCrumbEntity,
                    List<BreadCrumbEntity<PK>> breadCrumbEntityList){
        if (null == currentBreadCrumbEntity){
            //目前 原样返回, 将来可能支持自动排序
            //TODO 要点工作量
            return breadCrumbEntityList;
        }
        // 每次成一个新的
        List<BreadCrumbEntity<PK>> allParentBreadCrumbEntityList = new ArrayList<BreadCrumbEntity<PK>>();
        allParentBreadCrumbEntityList = constructParentBreadCrumbEntityList(
                        currentBreadCrumbEntity,
                        breadCrumbEntityList,
                        allParentBreadCrumbEntityList);

        LOGGER.info("before Collections.reverse,allParentBreadCrumbEntityList size:{}", allParentBreadCrumbEntityList.size());
        // 反转
        Collections.reverse(allParentBreadCrumbEntityList);
        return allParentBreadCrumbEntityList;
    }

    /**
     * 通过当前的BreadCrumbEntity,查找到所有的父节点.
     * <p style="color:red">
     * 递归生成
     * </p>
     *
     * @param <PK>
     *            the generic type
     * @param breadCrumbEntity
     *            the site map entity_in
     * @param siteMapEntities
     *            the site map entities
     * @param allParentBreadCrumbEntityList
     *            the all parent site map entity list
     * @return the list< bread crumb entity
     *         < p k>
     *         >
     */
    private static <PK> List<BreadCrumbEntity<PK>> constructParentBreadCrumbEntityList(
                    BreadCrumbEntity<PK> breadCrumbEntity,
                    List<BreadCrumbEntity<PK>> siteMapEntities,
                    List<BreadCrumbEntity<PK>> allParentBreadCrumbEntityList){
        // 加入到链式表
        allParentBreadCrumbEntityList.add(breadCrumbEntity);
        PK parentId = breadCrumbEntity.getParentId();

        // ****************************************************
        for (BreadCrumbEntity<PK> loopBreadCrumbEntity : siteMapEntities){
            // 当前的id和传入的breadCrumbEntity equals
            if (loopBreadCrumbEntity.getId().equals(parentId)){
                LOGGER.debug("loopBreadCrumbEntity.getId():{},breadCrumbEntity_in.getParentId():{}", loopBreadCrumbEntity.getId(), parentId);
                // 递归
                constructParentBreadCrumbEntityList(loopBreadCrumbEntity, siteMapEntities, allParentBreadCrumbEntityList);
                break;
            }
        }
        return allParentBreadCrumbEntityList;
    }

    /**
     * 匹配路径.
     *
     * @param <PK>
     *            the generic type
     * @param currentPath
     *            the current path
     * @param breadCrumbEntityList
     *            the bread crumb entity list
     * @return the site map entity by path
     */
    private static <PK> BreadCrumbEntity<PK> getBreadCrumbEntityByPath(String currentPath,List<BreadCrumbEntity<PK>> breadCrumbEntityList){
        for (BreadCrumbEntity<PK> breadCrumbEntity : breadCrumbEntityList){
            if (breadCrumbEntity.getPath().equals(currentPath)){
                return breadCrumbEntity;
            }
        }
        LOGGER.warn("currentPath is :{},can't find match BreadCrumbEntity", currentPath);
        return null;
    }
}
