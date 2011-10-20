package org.pillarone.riskanalytics.reporting.gira

import net.sf.jasperreports.engine.data.JRBeanCollectionDataSource
import net.sf.jasperreports.engine.data.JRMapCollectionDataSource
import org.pillarone.riskanalytics.reporting.gira.JasperChartUtils
import org.pillarone.riskanalytics.application.ui.util.SeriesColor
import org.pillarone.riskanalytics.application.ui.util.UIUtils
import org.pillarone.riskanalytics.application.util.JEstimator
import org.pillarone.riskanalytics.core.dataaccess.ResultAccessor
import org.pillarone.riskanalytics.core.output.AggregatedCollectingModeStrategy
import org.pillarone.riskanalytics.core.output.QuantilePerspective
import org.pillarone.riskanalytics.core.output.SimulationRun
import org.pillarone.riskanalytics.core.simulation.item.Simulation
import static org.pillarone.riskanalytics.reporting.gira.GiraReportHelper.format
import org.pillarone.riskanalytics.reporting.gira.GIRAReportModel

/**
 * @author fouad.jaada@intuitive-collaboration.com
 */
class ChartDataSourceFactory {

    protected ResultPathParser parser
    protected GiraReportHelper reportHelper
    protected ResultFunctionValuesBean valuesBean
    String modelName
    private colorsMap = [:]

    public ChartDataSourceFactory(Simulation simulation, ResultPathParser parser) {
        this.parser = parser
        modelName = simulation.modelClass.simpleName
        reportHelper = new GiraReportHelper(simulation: simulation)
        valuesBean = new ResultFunctionValuesBean(reportHelper.getSimulationRun(), AggregatedCollectingModeStrategy.IDENTIFIER)
    }

    JRMapCollectionDataSource getPDFChartsDataSource(List<List<String>> componentPaths, JRMapCollectionDataSource waterfallChart, PathType pathType) {
        List simpleMasterList = new ArrayList();
        for (List<String> paths: componentPaths) {
            getPeriodCount().times {int periodIndex ->
                Map pathPeriodMap = new HashMap();
                String pageTitle = reportHelper.getPageTitle(parser, paths[0], getPathType(paths[0], modelName), periodIndex)
                JRBeanCollectionDataSource fieldFunctionValues = getFieldFunctionValues(paths, periodIndex)
                pathPeriodMap["PDFChartAndCommentsInfo"] = GIRAReportUtils.getPDFChartAndCommentsInfoDataSource(reportHelper.getCommentsDataSource(paths, periodIndex), fieldFunctionValues, pageTitle)
                pathPeriodMap["chart"] = getChartDataSource(periodIndex, paths)
                pathPeriodMap["waterfallChart"] = waterfallChart
                pathPeriodMap["pageTitle"] = reportHelper.getComponentName(parser, paths[0]) + " " + reportHelper.getPageTitle(parser, paths[0], "", periodIndex)
                pathPeriodMap["overViewPageTitle"] = reportHelper.getComponentName(parser, paths[0]) + " " + AbstractWaterfallChart.getTitle(pathType)
                simpleMasterList << pathPeriodMap
            }
        }
        return new JRMapCollectionDataSource(simpleMasterList)
    }

    protected JRMapCollectionDataSource getChartDataSource(int periodIndex, List componentPaths) {
        Map seriesMap = [:]
        parser.appyFilter(componentPaths).each {String path ->
            String suffix = path.substring(path.lastIndexOf(":") + 1)
            GIRAReportUtils.FIELD_NAMES.each {String fieldName ->
                Map dbValues = getValues(periodIndex, path, AggregatedCollectingModeStrategy.IDENTIFIER, fieldName)
                if (dbValues) {
                    try {
                        String text = UIUtils.getText(GIRAReportModel, fieldName + suffix)
                        seriesMap[text] = JEstimator.adaptiveKernelBandwidthPdf(dbValues["values"], calcBandwidth(dbValues), false)
                        putColor(text)
                    } catch (Exception ex) {
                    }
                }
            }
        }
        return GIRAReportUtils.getRendererDataSource("pdfChart", JasperChartUtils.generatePDFChart(seriesMap, colorsMap))
    }

    public JRBeanCollectionDataSource getFieldFunctionValues(List<String> paths, int periodIndex) {
        Collection currentValues = new ArrayList()
        paths.each {String path ->
            String suffix = path.substring(path.lastIndexOf(":") + 1)
            GIRAReportUtils.FIELD_NAMES.each {String fieldName ->
                String meanValue = format(valuesBean.getMean(path, fieldName, periodIndex))
                String var955 = format(valuesBean.getVar(path, fieldName, periodIndex, 99.5))
                String tVar955 = format(valuesBean.getTvar(path, fieldName, periodIndex, 99.5))
                currentValues << ["functionName": UIUtils.getText(GIRAReportModel, fieldName + suffix), "meanValue": meanValue, "varValue": var955, "tVarValue": tVar955]
            }
        }
        JRBeanCollectionDataSource jrBeanCollectionDataSource = new JRBeanCollectionDataSource(currentValues);
        return jrBeanCollectionDataSource
    }

    protected double calcBandwidth(Map values) {
        return JEstimator.calcBandwidthForGaussKernelEstimate((double) values["stdDev"], (double) values["IQR"], values["n"])
    }

    int getPeriodCount() {
        reportHelper.periodCount
    }

    private String getPathType(String path, String modelName) {
        //todo
        return ""
    }

    private Map getValues(int periodIndex, String path, String collectorName, String fieldName) {
        try {
            SimulationRun run = reportHelper.getSimulationRun()
            boolean onlyStochasticSeries = ResultAccessor.hasDifferentValues(run, periodIndex, path, AggregatedCollectingModeStrategy.IDENTIFIER, fieldName)
            Map map = [:]
            if (onlyStochasticSeries) {
                map["stdDev"] = valuesBean.getStdDev(path, fieldName, periodIndex)
                map["mean"] = valuesBean.getMean(path, fieldName, periodIndex)

                Double per75 = ResultAccessor.getPercentile(run, periodIndex, path, collectorName, fieldName, 75, QuantilePerspective.LOSS)
                Double per25 = ResultAccessor.getPercentile(run, periodIndex, path, collectorName, fieldName, 25, QuantilePerspective.LOSS)
                if (per75 != null && per25 != null) {
                    map["IQR"] = per75 - per25
                }
                map["values"] = valuesBean.getValues(path, fieldName, periodIndex).sort()
                map["n"] = map["values"].size()
            } else {
                map["stdDev"] = 0
                map["mean"] = 0
                map["IQR"] = 0
                map["values"] = []
                map["n"] = 0
            }
            return map
        } catch (Exception ex) {
            ex.printStackTrace()
            return null
        }

    }

    void putColor(String key) {
        if (!colorsMap.containsKey(key)) {
            colorsMap[key] = SeriesColor.seriesColorList[colorsMap.keySet().size() + 1]
        }
    }


}