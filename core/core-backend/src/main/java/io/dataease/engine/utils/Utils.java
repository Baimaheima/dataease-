package io.dataease.engine.utils;

import io.dataease.api.ds.vo.DatasourceConfiguration;
import io.dataease.engine.constant.ExtFieldConstant;
import io.dataease.engine.constant.SQLConstants;
import io.dataease.engine.constant.SqlPlaceholderConstants;
import io.dataease.exception.DEException;
import io.dataease.extensions.datasource.dto.DatasourceSchemaDTO;
import io.dataease.extensions.view.dto.DatasetTableFieldDTO;
import io.dataease.extensions.view.model.SQLObj;
import io.dataease.i18n.Translator;
import org.apache.commons.lang3.ObjectUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.util.CollectionUtils;

import java.text.SimpleDateFormat;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public class Utils {
    public static boolean joinSort(String sort) {
        return (StringUtils.equalsIgnoreCase(sort, "asc") || StringUtils.equalsIgnoreCase(sort, "desc"));
    }

    // 解析计算字段
    public static String calcFieldRegex(String originField, SQLObj tableObj, List<DatasetTableFieldDTO> originFields, boolean isCross, Map<Long, DatasourceSchemaDTO> dsMap) {
        try {
            int i = 0;
            DatasourceConfiguration.DatasourceType datasourceType = null;
            if (dsMap != null && dsMap.entrySet().iterator().hasNext()) {
                Map.Entry<Long, DatasourceSchemaDTO> next = dsMap.entrySet().iterator().next();
                datasourceType = DatasourceConfiguration.DatasourceType.valueOf(next.getValue().getType());
            }
            return buildCalcField(originField, tableObj, originFields, i, isCross, datasourceType);
        } catch (Exception e) {
            DEException.throwException(Translator.get("i18n_field_circular_ref"));
        }
        return null;
    }

    public static String calcSimpleFieldRegex(String originField, SQLObj tableObj, List<DatasetTableFieldDTO> originFields, boolean isCross, Map<Long, String> dsTypeMap) {
        try {
            int i = 0;
            DatasourceConfiguration.DatasourceType datasourceType = null;
            if (dsTypeMap != null && dsTypeMap.entrySet().iterator().hasNext()) {
                Map.Entry<Long, String> next = dsTypeMap.entrySet().iterator().next();
                datasourceType = DatasourceConfiguration.DatasourceType.valueOf(next.getValue());
            }
            return buildCalcField(originField, tableObj, originFields, i, isCross, datasourceType);
        } catch (Exception e) {
            DEException.throwException(Translator.get("i18n_field_circular_ref"));
        }
        return null;
    }

    public static String buildCalcField(String originField, SQLObj tableObj, List<DatasetTableFieldDTO> originFields, int i, boolean isCross, DatasourceConfiguration.DatasourceType datasourceType) throws Exception {
        try {
            i++;
            if (i > 100) {
                DEException.throwException(Translator.get("i18n_field_circular_error"));
            }
            originField = originField.replaceAll("[\\t\\n\\r]]", "");
            // 正则提取[xxx]
            String regex = "\\[(.*?)]";
            Pattern pattern = Pattern.compile(regex);
            Matcher matcher = pattern.matcher(originField);
            Set<String> ids = new HashSet<>();
            while (matcher.find()) {
                String id = matcher.group(1);
                ids.add(id);
            }
            if (CollectionUtils.isEmpty(ids)) {
                return originField;
            }
            for (DatasetTableFieldDTO ele : originFields) {
                if (StringUtils.containsIgnoreCase(originField, ele.getId() + "")) {
                    // 计算字段允许二次引用，这里递归查询完整引用链
                    if (Objects.equals(ele.getExtField(), ExtFieldConstant.EXT_NORMAL)) {
                        if (isCross) {
                            originField = originField.replaceAll("\\[" + ele.getId() + "]", String.format(SQLConstants.FIELD_NAME, tableObj.getTableAlias(), ele.getDataeaseName()));
                        } else {
                            originField = originField.replaceAll("\\[" + ele.getId() + "]", datasourceType.getPrefix() + tableObj.getTableAlias() + datasourceType.getSuffix() + "." + datasourceType.getPrefix() + ele.getDataeaseName() + datasourceType.getSuffix());
                        }
                    } else {
                        originField = originField.replaceAll("\\[" + ele.getId() + "]", "(" + ele.getOriginName() + ")");
                        originField = buildCalcField(originField, tableObj, originFields, i, isCross, datasourceType);
                    }
                }
            }
            return originField;
        } catch (Exception e) {
            DEException.throwException(Translator.get("i18n_field_circular_error"));
        }
        return null;
    }

    public static String getLogic(String logic) {
        if (logic != null) {
            switch (logic) {
                case "and":
                    return "AND";
                case "or":
                    return "OR";
            }
        }
        return "AND";
    }

    public static String transDateFormat(String dateStyle, String datePattern) {
        String split = "-";
        if (StringUtils.equalsIgnoreCase(datePattern, "date_sub")) {
            split = "-";
        } else if (StringUtils.equalsIgnoreCase(datePattern, "date_split")) {
            split = "/";
        } else {
            split = "-";
        }

        if (StringUtils.isEmpty(dateStyle)) {
            return "yyyy-MM-dd HH:mm:ss";
        }

        switch (dateStyle) {
            case "y":
                return "yyyy";
            case "y_Q":
                return "CONCAT(%s,'" + split + "',%s)";
            case "y_M":
                return "yyyy" + split + "MM";
            case "y_W":
                return "%Y" + split + "%u";
            case "y_M_d":
                return "yyyy" + split + "MM" + split + "dd";
            case "H_m_s":
                return "HH:mm:ss";
            case "y_M_d_H_m":
                return "yyyy" + split + "MM" + split + "dd" + " HH:mm";
            case "y_M_d_H_m_s":
                return "yyyy" + split + "MM" + split + "dd" + " HH:mm:ss";
            default:
                return "yyyy-MM-dd HH:mm:ss";
        }
    }

    public static String transFilterTerm(String term) {
        switch (term) {
            case "eq":
                return " = ";
            case "not_eq":
                return " <> ";
            case "lt":
                return " < ";
            case "le":
                return " <= ";
            case "gt":
                return " > ";
            case "ge":
                return " >= ";
            case "in":
                return " IN ";
            case "not in":
                return " NOT IN ";
            case "like":
                return " LIKE ";
            case "not like":
                return " NOT LIKE ";
            case "null":
                return " IS NULL ";
            case "not_null":
                return " IS NOT NULL ";
            case "empty":
                return " = ";
            case "not_empty":
                return " <> ";
            case "between":
                return " BETWEEN ";
            default:
                return "";
        }
    }

    public static void checkCircularReference(String originField, List<DatasetTableFieldDTO> fields) {
        originField = originField.replaceAll("[\\t\\n\\r]]", "");
        // 正则提取[xxx]
        String regex = "\\[(.*?)]";
        Pattern pattern = Pattern.compile(regex);
        Matcher matcher = pattern.matcher(originField);
        while (matcher.find()) {
            Set<String> ids = new HashSet<>();
            String id = matcher.group(1);
            for (DatasetTableFieldDTO ele : fields) {
                if (StringUtils.containsIgnoreCase(id, ele.getId() + "")) {
                    if (ids.contains(id)) {
                        DEException.throwException(Translator.get("i18n_field_circular_ref"));
                    }
                    ids.add(id);
                    if (Objects.equals(ele.getExtField(), ExtFieldConstant.EXT_CALC)) {
                        originField = originField.replaceAll("\\[" + ele.getId() + "]", ele.getOriginName());
                        checkField(ids, originField, fields);
                    }
                }
            }
        }
    }

    public static void checkField(Set<String> ids, String originField, List<DatasetTableFieldDTO> fields) {
        originField = originField.replaceAll("[\\t\\n\\r]]", "");
        // 正则提取[xxx]
        String regex = "\\[(.*?)]";
        Pattern pattern = Pattern.compile(regex);
        Matcher matcher = pattern.matcher(originField);
        while (matcher.find()) {
            String id = matcher.group(1);
            for (DatasetTableFieldDTO ele : fields) {
                if (StringUtils.containsIgnoreCase(id, ele.getId() + "")) {
                    if (ids.contains(id)) {
                        DEException.throwException(Translator.get("i18n_field_circular_ref"));
                    }
                    ids.add(id);
                    if (Objects.equals(ele.getExtField(), ExtFieldConstant.EXT_CALC)) {
                        originField = originField.replaceAll("\\[" + ele.getId() + "]", ele.getOriginName());
                        checkField(ids, originField, fields);
                    }
                }
            }
        }
    }

    public static boolean matchFunction(String func, String originField) {
        String pattern = func + "\\s*\\((.*?)\\)";
        Pattern r = Pattern.compile(pattern, Pattern.CASE_INSENSITIVE);
        Matcher m = r.matcher(originField.replaceAll("[\\t\\n\\r]", ""));
        while (m.find()) {
            return true;
        }
        return false;
    }

    public static boolean isNeedOrder(List<String> dsList) {
        String[] list = {"sqlServer", "db2", "impala"};
        List<String> strings = Arrays.asList(list);
        List<String> collect = strings.stream().filter(dsList::contains).collect(Collectors.toList());
        return ObjectUtils.isNotEmpty(collect);
    }

    public static boolean isCrossDs(Map<Long, DatasourceSchemaDTO> dsMap) {
        return dsMap.size() != 1;
    }

    public static String replaceSchemaAlias(String sql, Map<Long, DatasourceSchemaDTO> dsMap) {
        DatasourceSchemaDTO value = dsMap.entrySet().iterator().next().getValue();
        return sql.replaceAll(SqlPlaceholderConstants.KEYWORD_PREFIX_REGEX + value.getSchemaAlias() + SqlPlaceholderConstants.KEYWORD_SUFFIX_REGEX + "\\.", "");
    }

    public static long allDateFormat2Long(String value) {
        String split = "-";
        if (value != null && value.contains("/")) {
            split = "/";
        }
        try {
            SimpleDateFormat simpleDateFormat = new SimpleDateFormat("yyyy" + split + "MM" + split + "dd HH:mm:ss");
            return simpleDateFormat.parse(value).getTime();
        } catch (Exception e) {
        }
        try {
            SimpleDateFormat simpleDateFormat = new SimpleDateFormat("yyyy" + split + "MM" + split + "dd HH:mm");
            return simpleDateFormat.parse(value).getTime();
        } catch (Exception e) {
        }
        try {
            SimpleDateFormat simpleDateFormat = new SimpleDateFormat("yyyy" + split + "MM" + split + "dd HH");
            return simpleDateFormat.parse(value).getTime();
        } catch (Exception e) {
        }
        try {
            SimpleDateFormat simpleDateFormat = new SimpleDateFormat("HH:mm:ss");
            return simpleDateFormat.parse(value).getTime();
        } catch (Exception e) {
        }
        try {
            SimpleDateFormat simpleDateFormat = new SimpleDateFormat("yyyy" + split + "MM" + split + "dd");
            return simpleDateFormat.parse(value).getTime();
        } catch (Exception e) {
        }
        try {
            SimpleDateFormat simpleDateFormat = new SimpleDateFormat("yyyy" + split + "MM");
            return simpleDateFormat.parse(value).getTime();
        } catch (Exception e) {
        }
        try {
            SimpleDateFormat simpleDateFormat = new SimpleDateFormat("yyyy");
            return simpleDateFormat.parse(value).getTime();
        } catch (Exception e) {
        }
        return 0;
    }

    public static String parseTime(String time, String sourceFormat, String targetFormat) {
        if (StringUtils.equalsIgnoreCase(sourceFormat, targetFormat)) {
            String[] s = time.split(" ");
            if (s.length > 1) {
                time = s[1];
            } else {
                time = s[0];
            }
        }
        return time;
    }

    public static Map<String, Long> parseDateTimeValue(String value) {
        Map<String, Long> map = new LinkedHashMap<>();
        long startTime = 0;
        long endTime = 0;

        String split = "-";
        if (value != null && value.contains("/")) {
            split = "/";
        }
        try {
            SimpleDateFormat simpleDateFormat = new SimpleDateFormat("yyyy" + split + "MM" + split + "dd HH:mm:ss");
            startTime = simpleDateFormat.parse(value).getTime();
            endTime = startTime + 999;

            map.put("startTime", startTime);
            map.put("endTime", endTime);
            return map;
        } catch (Exception e) {
        }
        try {
            SimpleDateFormat simpleDateFormat = new SimpleDateFormat("yyyy" + split + "MM" + split + "dd HH:mm");
            startTime = simpleDateFormat.parse(value).getTime();
            endTime = startTime + (60 * 1000 - 1);

            map.put("startTime", startTime);
            map.put("endTime", endTime);
            return map;
        } catch (Exception e) {
        }
        try {
            SimpleDateFormat simpleDateFormat = new SimpleDateFormat("yyyy" + split + "MM" + split + "dd HH");
            startTime = simpleDateFormat.parse(value).getTime();
            endTime = startTime + (60 * 60 * 1000 - 1);

            map.put("startTime", startTime);
            map.put("endTime", endTime);
            return map;
        } catch (Exception e) {
        }
        try {
            SimpleDateFormat simpleDateFormat = new SimpleDateFormat("HH:mm:ss");
            startTime = simpleDateFormat.parse(value).getTime();
            endTime = startTime + 999;

            map.put("startTime", startTime);
            map.put("endTime", endTime);
            return map;
        } catch (Exception e) {
        }
        try {
            SimpleDateFormat simpleDateFormat = new SimpleDateFormat("yyyy" + split + "MM" + split + "dd");
            startTime = simpleDateFormat.parse(value).getTime();
            endTime = startTime + (24 * 60 * 60 * 1000 - 1);

            map.put("startTime", startTime);
            map.put("endTime", endTime);
            return map;
        } catch (Exception e) {
        }
        try {
            SimpleDateFormat simpleDateFormat = new SimpleDateFormat("yyyy" + split + "MM");
            Date parse = simpleDateFormat.parse(value);
            startTime = parse.getTime();
            Calendar calendar = Calendar.getInstance();
            calendar.setTime(parse);
            calendar.add(Calendar.MONTH, 1);
            endTime = calendar.getTime().getTime() - 1;

            map.put("startTime", startTime);
            map.put("endTime", endTime);
            return map;
        } catch (Exception e) {
        }
        try {
            SimpleDateFormat simpleDateFormat = new SimpleDateFormat("yyyy");
            Date parse = simpleDateFormat.parse(value);
            startTime = parse.getTime();
            Calendar calendar = Calendar.getInstance();
            calendar.setTime(parse);
            calendar.add(Calendar.YEAR, 1);
            endTime = calendar.getTime().getTime() - 1;

            map.put("startTime", startTime);
            map.put("endTime", endTime);
            return map;
        } catch (Exception e) {
        }

        map.put("startTime", startTime);
        map.put("endTime", endTime);
        return map;
    }
}
