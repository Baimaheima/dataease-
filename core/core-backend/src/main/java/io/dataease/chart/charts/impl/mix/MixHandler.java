package io.dataease.chart.charts.impl.mix;

import io.dataease.api.chart.dto.ColumnPermissionItem;
import io.dataease.chart.charts.impl.YoyChartHandler;
import io.dataease.chart.utils.ChartDataBuild;
import io.dataease.datasource.provider.CalciteProvider;
import io.dataease.engine.utils.Utils;
import io.dataease.extensions.datasource.dto.DatasourceRequest;
import io.dataease.extensions.datasource.dto.DatasourceSchemaDTO;
import io.dataease.extensions.view.dto.*;
import io.dataease.extensions.view.model.SQLMeta;
import lombok.Getter;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.stream.Collectors;

@Component
public class MixHandler extends YoyChartHandler {
    @Getter
    private final String type = "chart-mix";

    @Override
    public AxisFormatResult formatAxis(ChartViewDTO view) {
        var axisMap = new HashMap<ChartAxis, List<ChartViewFieldDTO>>();
        var context = new HashMap<String, Object>();
        AxisFormatResult result = new AxisFormatResult(axisMap, context);
        //左轴分组子维度,非分组不需要
        axisMap.put(ChartAxis.xAxisExt, Collections.emptyList());
        //左轴堆叠子维度,非堆叠不需要
        axisMap.put(ChartAxis.extStack, Collections.emptyList());
        //左轴指标
        axisMap.put(ChartAxis.yAxis, view.getYAxis());
        //右轴分组子维度
        axisMap.put(ChartAxis.extBubble, view.getExtBubble());
        //右轴指标
        axisMap.put(ChartAxis.yAxisExt, view.getYAxisExt());
        //去除除了x轴以外的排序
        axisMap.forEach((k, v) -> {
            v.forEach(x -> x.setSort("none"));
        });
        axisMap.put(ChartAxis.extLabel, view.getExtLabel());
        axisMap.put(ChartAxis.extTooltip, view.getExtTooltip());
        //图表整体主维度
        var xAxis = new ArrayList<>(view.getXAxis());
        axisMap.put(ChartAxis.xAxis, xAxis);
        context.put("xAxisBase", xAxis);
        return result;
    }

    @Override
    public Map<String, Object> buildNormalResult(ChartViewDTO view, AxisFormatResult formatResult, CustomFilterResult filterResult, List<String[]> data) {
        boolean isDrill = filterResult
                .getFilterList()
                .stream()
                .anyMatch(ele -> ele.getFilterType() == 1);
        var xAxisBase = (List<ChartViewFieldDTO>) formatResult.getContext().get("xAxisBase");
        var yAxis = formatResult.getAxisMap().get(ChartAxis.yAxis);
        var xAxis = formatResult.getAxisMap().get(ChartAxis.xAxis);
        var xAxisExt = formatResult.getAxisMap().get(ChartAxis.xAxisExt);
        var result = ChartDataBuild.transMixChartDataAntV(xAxisBase, xAxis, xAxisExt, yAxis, view, data, isDrill);
        return result;
    }

    @Override
    public <T extends ChartCalcDataResult> T calcChartResult(ChartViewDTO view, AxisFormatResult formatResult, CustomFilterResult filterResult, Map<String, Object> sqlMap, SQLMeta sqlMeta, CalciteProvider provider) {
        //计算左轴, 包含 xAxis, yAxis
        var dsMap = (Map<Long, DatasourceSchemaDTO>) sqlMap.get("dsMap");
        List<String> dsList = new ArrayList<>();
        for (Map.Entry<Long, DatasourceSchemaDTO> next : dsMap.entrySet()) {
            dsList.add(next.getValue().getType());
        }
        boolean needOrder = Utils.isNeedOrder(dsList);
        boolean crossDs = Utils.isCrossDs(dsMap);
        var leftResult = (T) super.calcChartResult(view, formatResult, filterResult, sqlMap, sqlMeta, provider);
        var dynamicAssistFields = getDynamicAssistFields(view);
        try {
            //如果有同环比过滤,应该用原始sql
            var originSql = leftResult.getQuerySql();
            var leftAssistFields = dynamicAssistFields.stream().filter(x -> StringUtils.equalsAnyIgnoreCase(x.getYAxisType(), "left")).toList();
            var yAxis = formatResult.getAxisMap().get(ChartAxis.yAxis);
            var assistFields = getAssistFields(leftAssistFields, yAxis);
            if (CollectionUtils.isNotEmpty(assistFields)) {
                var req = new DatasourceRequest();
                req.setDsList(dsMap);
                var assistSql = assistSQL(originSql, assistFields);
                req.setQuery(assistSql);
                logger.info("calcite assistSql sql: " + assistSql);
                var assistData = (List<String[]>) provider.fetchResultField(req).get("data");
                leftResult.setAssistData(assistData);
                leftResult.setDynamicAssistFields(leftAssistFields);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        // 计算右轴，包含 xAxis,xAxisExt,yAxisExt,需要去掉 group 和 stack
        var xAxis = formatResult.getAxisMap().get(ChartAxis.xAxis);
        var extStack = formatResult.getAxisMap().get(ChartAxis.extStack);
        var xAxisExt = formatResult.getAxisMap().get(ChartAxis.xAxisExt);
        xAxis = xAxis.subList(0, xAxis.size() - extStack.size() - xAxisExt.size());
        var extBubble = formatResult.getAxisMap().get(ChartAxis.extBubble);
        xAxis.addAll(extBubble);
        formatResult.getAxisMap().put(ChartAxis.xAxis, xAxis);
        formatResult.getAxisMap().put(ChartAxis.xAxisExt, extBubble);
        var yAxisExt = formatResult.getAxisMap().get(ChartAxis.yAxisExt);
        formatResult.getAxisMap().put(ChartAxis.yAxis, yAxisExt);
        formatResult.getContext().remove("yoyFiltered");
        // 右轴重新检测同环比过滤
        customFilter(view, filterResult.getFilterList(), formatResult);
        var rightResult = (T) super.calcChartResult(view, formatResult, filterResult, sqlMap, sqlMeta, provider);
        try {
            //如果有同环比过滤,应该用原始sql
            var originSql = rightResult.getQuerySql();
            var rightAssistFields = dynamicAssistFields.stream().filter(x -> StringUtils.equalsAnyIgnoreCase(x.getYAxisType(), "right")).toList();
            var yAxis = formatResult.getAxisMap().get(ChartAxis.yAxis);
            var assistFields = getAssistFields(rightAssistFields, yAxis);
            if (CollectionUtils.isNotEmpty(assistFields)) {
                var req = new DatasourceRequest();
                req.setDsList(dsMap);
                var assistSql = assistSQL(originSql, assistFields);
                req.setQuery(assistSql);
                var assistData = (List<String[]>) provider.fetchResultField(req).get("data");
                rightResult.setAssistData(assistData);
                rightResult.setDynamicAssistFields(rightAssistFields);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        var mixResult = (T) new ChartCalcDataResult();
        var data = new HashMap<String, Object>();
        data.put("left", leftResult);
        data.put("right", rightResult);
        mixResult.setData(data);
        mixResult.setContext(filterResult.getContext());
        return mixResult;
    }

    @Override
    public ChartViewDTO buildChart(ChartViewDTO view, ChartCalcDataResult calcResult, AxisFormatResult formatResult, CustomFilterResult filterResult) {
        var desensitizationList = (Map<String, ColumnPermissionItem>) filterResult.getContext().get("desensitizationList");
        var leftCalcResult = (ChartCalcDataResult) calcResult.getData().get("left");
        var leftFields = new ArrayList<ChartViewFieldDTO>();
        leftFields.addAll(view.getXAxis());
        leftFields.addAll(view.getYAxis());
        mergeAssistField(leftCalcResult.getDynamicAssistFields(), leftCalcResult.getAssistData());
        var leftOriginData = leftCalcResult.getOriginData();
        var leftTable = ChartDataBuild.transTableNormal(leftFields, view, leftOriginData, desensitizationList);
        var leftData = new HashMap<String, Object>(leftTable);
        leftData.putAll(leftCalcResult.getData());
        leftData.put("dynamicAssistLines", leftCalcResult.getDynamicAssistFields());

        var rightCalcResult = (ChartCalcDataResult) calcResult.getData().get("right");
        var rightFields = new ArrayList<ChartViewFieldDTO>();
        rightFields.addAll(view.getXAxis());
        rightFields.addAll(view.getExtBubble());
        rightFields.addAll(view.getYAxisExt());
        mergeAssistField(rightCalcResult.getDynamicAssistFields(), rightCalcResult.getAssistData());
        var rightOriginData = rightCalcResult.getOriginData();
        var rightTable = ChartDataBuild.transTableNormal(rightFields, view, rightOriginData, desensitizationList);
        var rightData = new HashMap<String, Object>(leftTable);
        rightData.putAll(rightCalcResult.getData());
        rightData.put("dynamicAssistLines", rightCalcResult.getDynamicAssistFields());

        var allFields = (List<ChartViewFieldDTO>) filterResult.getContext().get("allFields");
        // 构建结果
        Map<String, Object> chartData = new TreeMap<>();
        chartData.put("left", leftData);
        chartData.put("right", rightData);

        var drillFilters = filterResult.getFilterList().stream().filter(f -> f.getFilterType() == 1).collect(Collectors.toList());
        var isDrill = CollectionUtils.isNotEmpty(drillFilters);
        view.setDrillFilters(drillFilters);
        view.setDrill(isDrill);
        view.setSql(leftCalcResult.getQuerySql());
        view.setData(chartData);
        return view;
    }
}
