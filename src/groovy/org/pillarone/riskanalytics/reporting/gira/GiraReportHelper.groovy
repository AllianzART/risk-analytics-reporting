package org.pillarone.riskanalytics.reporting.gira

import org.pillarone.riskanalytics.core.simulation.item.Simulation
import net.sf.jasperreports.engine.data.JRBeanCollectionDataSource
import org.pillarone.riskanalytics.core.simulation.item.parameter.comment.Comment
import org.pillarone.riskanalytics.core.user.Person
import org.pillarone.riskanalytics.core.user.UserManagement
import java.text.NumberFormat
import org.pillarone.riskanalytics.core.output.SimulationRun
import org.joda.time.DateTime
import org.pillarone.riskanalytics.core.parameter.comment.Tag
import org.pillarone.riskanalytics.core.simulation.item.parameter.comment.EnumTagType
import org.pillarone.riskanalytics.application.ui.util.CommentUtils
import org.pillarone.riskanalytics.application.ui.util.UIUtils
import org.pillarone.riskanalytics.application.ui.comment.view.CommentAndErrorView
import org.pillarone.riskanalytics.application.ui.comment.view.CommentPane
import org.pillarone.riskanalytics.application.ui.util.DateFormatUtils
import org.pillarone.riskanalytics.application.reports.comment.action.CommentReportAction
import org.pillarone.riskanalytics.application.ui.comment.view.NewCommentView
import org.pillarone.riskanalytics.application.ui.result.model.ResultViewUtils
import org.pillarone.riskanalytics.application.util.LocaleResources

/**
 * @author fouad.jaada@intuitive-collaboration.com
 */
class GiraReportHelper {

    protected Simulation simulation
    Map periodLabels = [:]
    static NumberFormat numberFormat
    static NumberFormat percentageFormat

    public JRBeanCollectionDataSource getCommentsDataSource(List<String> paths, int periodIndex) {
        Collection currentValues = new ArrayList<Comment>()
        for (String path: paths) {
            for (Comment comment: getComments(path, periodIndex)) {
                addCommentData(comment, currentValues)
            }
        }

        JRBeanCollectionDataSource jrBeanCollectionDataSource = new JRBeanCollectionDataSource(currentValues);
        return jrBeanCollectionDataSource
    }

    public void addCommentData(Comment comment, Collection currentValues) {
        String boxTitle = CommentUtils.getCommentTitle(comment, simulation.modelClass)
        StringBuilder commentInfo = new StringBuilder("Period: ")
        String username = comment.user ? comment.user.username : ""
        commentInfo.append((comment.getPeriod() != -1) ? getPeriodLabel(comment.getPeriod()) : " " + UIUtils.getText(CommentAndErrorView.class, "forAllPeriods"))
        if (username != "")
            commentInfo.append(" " + UIUtils.getText(CommentPane.class, "user") + ": " + username)
        commentInfo.append(" Date: " + DateFormatUtils.formatDetailed(comment.lastChange))
        String tags = CommentUtils.getTagsValue(comment).replaceAll("<br>", ", ")
        String addedFiles = UIUtils.getText(CommentReportAction.class, "attachments") + ": " + (comment.getFiles() as List).join(", ")
        currentValues << ["boxTitle": boxTitle, "commentInfo": commentInfo.toString(), "tags": tags, "addedFiles": addedFiles, "text": comment.getText()]
    }

    List<Comment> getComments(String path, int periodIndex) {
        List<Comment> comments = []
        Tag reportTag = Tag.findByNameAndTagType(NewCommentView.REPORT, EnumTagType.COMMENT)
        GIRAReportUtils.FIELD_NAMES.each {String fieldName ->
            String commentPath = path + ":" + fieldName
            Collection<Comment> pathFieldComments = simulation.comments.findAll {Comment comment ->
                comment.path == commentPath && (comment.period == -1 || comment.period == periodIndex)
            }
            Collection<Comment> reportComments = pathFieldComments.findAll {Comment comment ->
                comment.tags.contains(reportTag)
            }
            comments.addAll(reportComments)
        }
        return comments
    }

    String getPageTitle(ResultPathParser parser, String path, String type, int period) {
        String pageTitle = getComponentName(parser, path)

        String nodeName = ResultViewUtils.getResultNodesDisplayName(simulation?.modelClass, path)
        if (nodeName)
            pageTitle += ", " + nodeName
        if (type)
            pageTitle += ", " + type
        String periodLabel = getPeriodLabel(period)
        pageTitle += ", Period Starting at " + periodLabel
        return pageTitle
    }

    String getComponentName(ResultPathParser parser, String path) {
        String pageTitle = ""
        PathType pathType = parser.getPathType(path)
        if (pathType)
            pageTitle += pathType.getDispalyName()
        return pageTitle
    }

    public String getPeriodLabel(int periodIndex) {
        String label
        if (periodLabels[periodIndex]) {
            label = periodLabels[periodIndex]
        } else {
            ResultViewUtils.initPeriodLabels(simulation.getSimulationRun(), periodLabels)
            label = periodLabels[periodIndex]
        }
        return label
    }

    public int getPeriodCount() {
        return simulation.periodCount
    }

    SimulationRun getSimulationRun() {
        return simulation.simulationRun
    }

    public static URL getReportFolder() {
        return GiraReportHelper.class.getResource("/reports")
    }

    static String getFooter() {
        StringBuilder sb = new StringBuilder()
        Person currentUser = UserManagement.getCurrentUser()
        sb.append(currentUser ? UIUtils.getText(GiraReportHelper.class, "footerByUser", [currentUser.username]) : UIUtils.getText(GiraReportHelper.class, "footer"))
        sb.append(" " + DateFormatUtils.formatDetailed(new DateTime()))
        return sb.toString()
    }

    static NumberFormat getNumberFormat() {
        if (!numberFormat) {
            numberFormat = LocaleResources.getNumberFormat()
            numberFormat.setMaximumFractionDigits(0)
        }
        return numberFormat
    }

    static NumberFormat getPercentageFormat() {
        if (!percentageFormat) {
            percentageFormat = NumberFormat.getPercentInstance(UIUtils.clientLocale)
        }
        return percentageFormat
    }

    static String format(Double value) {
        try {
            return getNumberFormat().format(value)
        } catch (Exception ex) {
            return "-"
        }
    }

    static String formatPercentage(Double value) {
        try {
            return getPercentageFormat().format(value)
        }
        catch (Exception ex) {
            return '-'
        }
    }
}
