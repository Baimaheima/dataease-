package io.dataease.chart.charts.impl.table;

import io.dataease.api.chart.dto.PageInfo;
import io.dataease.chart.charts.impl.DefaultChartHandler;
import io.dataease.dataset.utils.SqlUtils;
import io.dataease.datasource.provider.CalciteProvider;
import io.dataease.engine.sql.SQLProvider;
import io.dataease.engine.trans.Dimension2SQLObj;
import io.dataease.engine.utils.Utils;
import io.dataease.extensions.datasource.dto.DatasourceRequest;
import io.dataease.extensions.datasource.dto.DatasourceSchemaDTO;
import io.dataease.extensions.view.dto.*;
import io.dataease.extensions.view.model.SQLMeta;
import io.dataease.extensions.view.util.ChartDataUtil;
import io.dataease.extensions.view.util.FieldUtil;
import lombok.Getter;
import org.apache.commons.lang3.ObjectUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Component
public class TableInfoHandler extends DefaultChartHandler {
    @Getter
    private String type = "table-info";

    @Override
    public AxisFormatResult formatAxis(ChartViewDTO view) {
        var result = super.formatAxis(view);
        result.getAxisMap().put(ChartAxis.yAxis, new ArrayList<>());
        return result;
    }

    @Override
    public <T extends CustomFilterResult, K extends AxisFormatResult> T customFilter(ChartViewDTO view, List<ChartExtFilterDTO> filterList, K formatResult) {
        var chartExtRequest = view.getChartExtRequest();
        Map<String, Object> mapAttr = view.getCustomAttr();
        Map<String, Object> mapSize = (Map<String, Object>) mapAttr.get("basicStyle");
        var tablePageMode = (String) mapSize.get("tablePageMode");
        formatResult.getContext().put("tablePageMode", tablePageMode);
        if (StringUtils.equalsIgnoreCase(tablePageMode, "page") && !view.getIsExcelExport()) {
            if (chartExtRequest.getGoPage() == null) {
                chartExtRequest.setGoPage(1L);
            }
            if (chartExtRequest.getPageSize() == null) {
                int pageSize = (int) mapSize.get("tablePageSize");
                if (StringUtils.equalsIgnoreCase(view.getResultMode(), "custom")) {
                    chartExtRequest.setPageSize(Math.min(pageSize, view.getResultCount().longValue()));
                } else {
                    chartExtRequest.setPageSize((long) pageSize);
                }
            }
        } else {
            if (StringUtils.equalsIgnoreCase(view.getResultMode(), "custom")) {
                chartExtRequest.setGoPage(1L);
                chartExtRequest.setPageSize(view.getResultCount().longValue());
            } else if (!view.getIsExcelExport()) {
                chartExtRequest.setGoPage(null);
                chartExtRequest.setPageSize(null);
            }
        }
        return super.customFilter(view, filterList, formatResult);
    }

    @Override
    public <T extends ChartCalcDataResult> T calcChartResult(ChartViewDTO view, AxisFormatResult formatResult, CustomFilterResult filterResult, Map<String, Object> sqlMap, SQLMeta sqlMeta, CalciteProvider provider) {
        var chartExtRequest = view.getChartExtRequest();
        var dsMap = (Map<Long, DatasourceSchemaDTO>) sqlMap.get("dsMap");
        List<String> dsList = new ArrayList<>();
        for (Map.Entry<Long, DatasourceSchemaDTO> next : dsMap.entrySet()) {
            dsList.add(next.getValue().getType());
        }
        boolean needOrder = Utils.isNeedOrder(dsList);
        boolean crossDs = Utils.isCrossDs(dsMap);
        DatasourceRequest datasourceRequest = new DatasourceRequest();
        datasourceRequest.setDsList(dsMap);
        var xAxis = formatResult.getAxisMap().get(ChartAxis.xAxis);
        var allFields = getAllChartFields(view);
        PageInfo pageInfo = new PageInfo();
        pageInfo.setGoPage(chartExtRequest.getGoPage());
        if (StringUtils.equalsIgnoreCase(view.getResultMode(), "custom")) {
            pageInfo.setPageSize(Math.min(view.getResultCount() - (chartExtRequest.getGoPage() - 1) * chartExtRequest.getPageSize(), chartExtRequest.getPageSize()));
        } else {
            pageInfo.setPageSize(chartExtRequest.getPageSize());
        }
        Dimension2SQLObj.dimension2sqlObj(sqlMeta, xAxis, FieldUtil.transFields(allFields), crossDs, dsMap);
        String originSql = SQLProvider.createQuerySQL(sqlMeta, false, true, view);// 明细表强制加排序
        String limit = ((pageInfo.getGoPage() != null && pageInfo.getPageSize() != null) ? " LIMIT " + pageInfo.getPageSize() + " OFFSET " + (pageInfo.getGoPage() - 1) * pageInfo.getPageSize() : "");
        var querySql = originSql + limit;

        var tablePageMode = (String) filterResult.getContext().get("tablePageMode");
        var totalPageSql = "SELECT COUNT(*) FROM (" + SQLProvider.createQuerySQL(sqlMeta, false, false, view) + ") COUNT_TEMP";
        if (StringUtils.isNotEmpty(totalPageSql) && StringUtils.equalsIgnoreCase(tablePageMode, "page")) {
            totalPageSql = SqlUtils.rebuildSQL(totalPageSql, sqlMeta, crossDs, dsMap);
            datasourceRequest.setQuery(totalPageSql);
            datasourceRequest.setTotalPageFlag(true);
            logger.info("calcite total sql: " + totalPageSql);
            List<String[]> tmpData = (List<String[]>) provider.fetchResultField(datasourceRequest).get("data");
            var totalItems = ObjectUtils.isEmpty(tmpData) ? 0 : Long.valueOf(tmpData.get(0)[0]);
            if (StringUtils.equalsIgnoreCase(view.getResultMode(), "custom")) {
                totalItems = totalItems <= view.getResultCount() ? totalItems : view.getResultCount();
            }
            var totalPage = (totalItems / pageInfo.getPageSize()) + (totalItems % pageInfo.getPageSize() > 0 ? 1 : 0);
            view.setTotalItems(totalItems);
            view.setTotalPage(totalPage);
        }

        querySql = SqlUtils.rebuildSQL(querySql, sqlMeta, crossDs, dsMap);
        datasourceRequest.setQuery(querySql);
        logger.info("calcite chart sql: " + querySql);
        List<String[]> data = (List<String[]>) provider.fetchResultField(datasourceRequest).get("data");
        //自定义排序
        data = ChartDataUtil.resultCustomSort(xAxis, data);
        //数据重组逻辑可重载
        var result = this.buildResult(view, formatResult, filterResult, data);
        T calcResult = (T) new ChartCalcDataResult();
        calcResult.setData(result);
        calcResult.setContext(filterResult.getContext());
        calcResult.setQuerySql(querySql);
        calcResult.setOriginData(data);
        return calcResult;
    }
}
