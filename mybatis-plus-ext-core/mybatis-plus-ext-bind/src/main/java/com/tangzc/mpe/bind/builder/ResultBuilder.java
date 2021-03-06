package com.tangzc.mpe.bind.builder;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.tangzc.mpe.base.MapperScanner;
import com.tangzc.mpe.bind.metadata.FieldDescription;
import com.tangzc.mpe.bind.metadata.JoinConditionDescription;
import lombok.AllArgsConstructor;

import java.lang.reflect.InvocationTargetException;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * @author don
 */
@AllArgsConstructor(staticName = "newInstance")
public class ResultBuilder<BEAN, ENTITY> {

    private final List<BEAN> beans;
    private final ConditionSign<ENTITY, JoinConditionDescription> conditionSign;
    private final List<? extends FieldDescription<?, JoinConditionDescription>> fieldDescriptions;
    private final FillDataCallback fillDataCallback;

    public void fillData() {

        Map<String, List<ENTITY>> entityMap = listEntitiesByCondition();

        for (BEAN bean : beans) {

            String entryGroupKey = getGroupKeyOfBean(bean, conditionSign);
            List<?> entities = entityMap.getOrDefault(entryGroupKey, Collections.emptyList());

            for (FieldDescription<?, JoinConditionDescription> fieldAnnotation : fieldDescriptions) {

                List<?> dataList = entities;
                if (fillDataCallback != null) {
                    fillDataCallback.fillBefore(bean, fieldAnnotation, dataList);
                    dataList = fillDataCallback.changeDataList(bean, fieldAnnotation, dataList);
                }

                fullDataToBeanField(bean, fieldAnnotation, dataList);
            }
        }
    }

    private Map<String, List<ENTITY>> listEntitiesByCondition() {

        List<JoinConditionDescription> conditions = conditionSign.getConditions();

        QueryWrapper<ENTITY> queryWrapper = QueryWrapperBuilder.<ENTITY>newInstance()
                .select(fillDataCallback.selectColumns(beans, conditionSign, fieldDescriptions))
                .where(conditions, conditionSign.getCustomCondition())
                .orderBy(conditionSign.getOrderBys())
                .build(beans);

        Class<ENTITY> joinEntityClass = conditionSign.getJoinEntityClass();
        BaseMapper<ENTITY> mapper = MapperScanner.getMapper(joinEntityClass);
        List<ENTITY> entities = mapper.selectList(queryWrapper);

        return entities.stream()
                .collect(Collectors.groupingBy(
                        entity -> getGroupKeyOfEntity(entity, conditionSign)
                ));
    }

    private void fullDataToBeanField(BEAN bean,
                                     FieldDescription<?, JoinConditionDescription> fieldAnnotation,
                                     List<?> dataList) {

        Object val;

        if (fieldAnnotation.isCollection()) {
            val = dataList;
        } else {
            // ??????????????????
            checkBindDataSize(bean.getClass().getName(), dataList.size(), fieldAnnotation.getFieldName());
            val = dataList.isEmpty() ? null : dataList.get(0);
        }

        try {
            fieldAnnotation.getSetMethod().invoke(bean, val);
        } catch (IllegalAccessException | InvocationTargetException e) {
            e.printStackTrace();
        }
    }

    /**
     * ?????????????????????????????????????????????????????????????????????????????????????????????????????????
     */
    private void checkBindDataSize(String beanName, int dataListSize, String fieldName) {

        if (dataListSize > 1) {
            String entityName = conditionSign.getJoinEntityClass().getName();
            throw new RuntimeException(beanName + "." + fieldName
                    + "??????" + entityName + "?????????????????????1???");
        }
    }

    private String getGroupKeyOfBean(BEAN bean, ConditionSign<ENTITY, JoinConditionDescription> conditionSign) {

        return conditionSign.getConditions().stream()
                .map(condition -> {
                    try {
                        return condition.getJoinField() + "=" + condition.getSelfFieldGetMethod().invoke(bean);
                    } catch (Exception e) {
                        e.printStackTrace();
                        return null;
                    }
                }).collect(Collectors.joining("|"));
    }

    private String getGroupKeyOfEntity(ENTITY entity, ConditionSign<ENTITY, JoinConditionDescription> conditionSign) {

        return conditionSign.getConditions().stream()
                .map(condition -> {
                    try {
                        return condition.getJoinField() + "=" + condition.getJoinFieldGetMethod().invoke(entity);
                    } catch (Exception e) {
                        e.printStackTrace();
                        return null;
                    }
                }).collect(Collectors.joining("|"));
    }

    public interface FillDataCallback {

        /**
         * ???????????????????????????null????????????0????????????
         */
        default String[] selectColumns(List<?> beans,
                                       ConditionSign<?, JoinConditionDescription> entityJoinCondition,
                                       List<? extends FieldDescription<?, JoinConditionDescription>> fieldAnnotationList) {
            return null;
        }

        /**
         * ????????????????????????????????????????????????
         */
        default void fillBefore(Object bean, FieldDescription<?, JoinConditionDescription> fieldAnnotation, List<?> entities) {

        }

        /**
         * ?????????????????????????????????????????????????????????????????????????????????Bean?????????
         */
        default List<?> changeDataList(Object bean, FieldDescription<?, JoinConditionDescription> fieldAnnotation, List<?> entities) {
            return entities;
        }
    }
}
