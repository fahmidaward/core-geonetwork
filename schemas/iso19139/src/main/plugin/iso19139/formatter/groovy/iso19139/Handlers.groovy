package iso19139
import org.fao.geonet.services.metadata.format.groovy.Environment

public class Handlers {
    protected org.fao.geonet.services.metadata.format.groovy.Handlers handlers;
    protected org.fao.geonet.services.metadata.format.groovy.Functions f
    protected Environment env
    Matchers matchers
    Functions isofunc
    common.Handlers commonHandlers
    String[] packageViews

    public Handlers(handlers, f, env) {
        this.handlers = handlers
        this.f = f
        this.env = env
        isofunc = new Functions(handlers: handlers, f:f, env:env)
        matchers =  new Matchers(handlers: handlers, f:f, env:env)
        commonHandlers = new common.Handlers(handlers, f, env)
        packageViews = [
                'gmd:identificationInfo', 'gmd:metadataMaintenance', 'gmd:metadataConstraints', 'gmd:spatialRepresentationInfo',
                'gmd:distributionInfo', 'gmd:applicationSchemaInfo', 'gmd:dataQualityInfo', 'gmd:portrayalCatalogueInfo',
                'gmd:contentInfo', 'gmd:metadataExtensionInfo', 'gmd:referenceSystemInfo']
    }

    def addDefaultHandlers() {
        handlers.add name: 'Text Elements', select: matchers.isTextEl, isoTextEl
        handlers.add name: 'URL Elements', select: matchers.isUrlEl, isoUrlEl
        handlers.add name: 'Simple Elements', select: matchers.isSimpleEl, isoSimpleEl
        handlers.add name: 'Boolean Elements', select: matchers.isBooleanEl, isoBooleanEl
        handlers.add name: 'CodeList Elements', select: matchers.isCodeListEl, isoCodeListEl
        handlers.add name: 'Date Elements', select: matchers.isDateEl, dateEl
        handlers.add name: 'Elements with single Date child', select: matchers.hasDateChild, commonHandlers.applyToChild(isoCodeListEl, '*')
        handlers.add name: 'Elements with single Codelist child', select: matchers.hasCodeListChild, commonHandlers.applyToChild(isoCodeListEl, '*')
        handlers.add name: 'ResponsibleParty Elements', select: matchers.isRespParty, pointOfContactEl
        handlers.add 'gmd:CI_OnlineResource', commonHandlers.entryEl(f.&nodeLabel)

        handlers.skip matchers.isSkippedContainer, {it.children()}

        handlers.add 'gmd:locale', localeEl
        handlers.add 'gmd:CI_Date', ciDateEl
        handlers.add 'gmd:CI_Citation', citationEl
        handlers.add name: 'Root Element', select: matchers.isRoot, rootPackageEl

        handlers.add name: 'identificationInfo elements', select: {it.parent().name() == 'gmd:identificationInfo'}, commonHandlers.entryEl(f.&nodeLabel, {el -> 'gmd_identificationInfo'})
        handlers.add name: 'Container Elements', select: matchers.isContainerEl, priority: -1, commonHandlers.entryEl(f.&nodeLabel, addPackageViewClass)

        commonHandlers.addDefaultStartAndEndHandlers();
        addExtentHandlers()

        handlers.sort name: 'Text Elements', select: 'gmd:MD_Metadata'/*matchers.isContainerEl*/, priority: -1, {el1, el2 ->
            def v1 = matchers.isContainerEl(el1) ? 1 : -1;
            def v2 = matchers.isContainerEl(el2) ? 1 : -1;
            return v1 - v2
        }
    }

    def addPackageViewClass = {el -> if (packageViews.contains(el.name())) return el.name().replace(':', '_')}

    def addExtentHandlers() {
        handlers.add commonHandlers.matchers.hasChild('gmd:EX_Extent'), commonHandlers.flattenedEntryEl({it.'gmd:EX_Extent'}, f.&nodeLabel)
        handlers.add name: 'BBox Element', select: matchers.isBBox, bboxEl
        handlers.add 'gmd:geographicElement', commonHandlers.processChildren{it.children()}
        handlers.add 'gmd:extentTypeCode', extentTypeCodeEl
    }

    def isoTextEl = { commonHandlers.func.textEl(f.nodeLabel(it), isofunc.isoText(it))}
    def isoUrlEl = { commonHandlers.func.textEl(f.nodeLabel(it), it.'gmd:Url'.text())}
    def isoCodeListEl = {commonHandlers.func.textEl(f.nodeLabel(it), f.codelistValueLabel(it))}
    def isoSimpleEl = {commonHandlers.func.textEl(f.nodeLabel(it), it.'*'.text())}
    def parseBool(text) {
        switch (text.trim().toLowerCase()){
            case "1":
            case "true":
            case "y":
                return true;
            default:
                return false;
        }
    }
    def isoBooleanEl = {commonHandlers.func.textEl(f.nodeLabel(it), parseBool(it.'*'.text()).toString())}
    def dateEl = {commonHandlers.func.textEl(f.nodeLabel(it), it.text());}
    def extentTypeCodeEl = {
        commonHandlers.func.textEl(f.nodeLabel(it), parseBool(it.text()) ? 'include' : 'excluded')
    }
    def ciDateEl = {
        if(!it.'gmd:date'.'gco:Date'.text().isEmpty()) {
            def dateType = f.codelistValueLabel(it.'gmd:dateType'.'gmd:CI_DateTypeCode')
            commonHandlers.func.textEl(dateType, it.'gmd:date'.'gco:Date'.text());
        }
    }

    def localeEl = { el ->
        def ptLocale = el.'gmd:PT_Locale'
        def toHtml = commonHandlers.when(matchers.isCodeListEl, commonHandlers.span(f.&codelistValueLabel))

        def data = [toHtml(ptLocale.'gmd:languageCode'.'gmd:LanguageCode'),
                    toHtml(ptLocale.'gmd:country'.'gmd:Country')]

        def nonEmptyEls = data.findAll{it != null}
        '<p> -- TODO Need widget for gmd:PT_Locale -- ' + nonEmptyEls.join("") + ' -- </p>'
    }

    def skipContainer = {
        el ->
            handlers.processElements(el.children())
    }

    def citationEl = { el ->
        def output = '<div class="row">'
        output += commonHandlers.func.textColEl(handlers.processElements([el.'gmd:title', el.'gmd:alternateTitle']), 8)
        def dateContent = handlers.processElements(el.'gmd:date'.'gmd:CI_Date')
        if (el.'gmd:edition' && el.'gmd:editionDate') {
            dateContent += commonHandlers.func.textEl(el.'gmd:edition'.'gco:CharacterString'.text(),
                    el.'gmd:editionDate'.'gco:Date'.text())
        }
        output += commonHandlers.func.textColEl(dateContent, 4)
        output += '</div>'

        output += '<div class="row">'
        def infoContent = ''
        if(!el.'gmd:identifier'.text().isEmpty()) {
            infoContent += commonHandlers.func.textEl(f.nodeLabel(el.'gmd:identifier'),
                    el.'gmd:identifier'.'gmd:MD_Identifier'.'gmd:code'.'gco:CharacterString'.text())
        }
        if(!el.'gmd:presentationForm'.text().isEmpty()) {
            infoContent += commonHandlers.func.textEl(f.nodeLabel(el.'gmd:presentationForm'),
                    f.codelistValueLabel(el.'gmd:presentationForm'.'gmd:CI_PresentationFormCode'))
        }
        infoContent += handlers.processElements([el.'gmd:ISBN', el.'gmd:ISSN'])
        output += commonHandlers.func.textColEl(infoContent, 4)
        output += '</div>'

        def processedChildren = ['gmd:title', 'gmd:alternateTitle', 'gmd:identifier', 'gmd:ISBN', 'gmd:ISSN',
                                 'gmd:date', 'gmd:edition', 'gmd:editionDate', 'gmd:presentationForm']

        def otherChildrens = el.children().findAll { ch -> !processedChildren.contains(ch.name()) }
        output += handlers.processElements(otherChildrens)

        return output
    }

    /**
     * El must be a parent of gmd:CI_ResponsibleParty
     */
    def pointOfContactEl = { el ->

        def party = el.children().find { ch ->
            ch.name() == 'gmd:CI_ResponsibleParty' || ch['@gco:isoType'].text() == 'gmd:CI_ResponsibleParty'
        }

        def generalChildren = [
                party.'gmd:individualName',
                party.'gmd:organisationName',
                party.'gmd:positionName',
                party.'gmd:role'
        ]
        def general = handlers.fileResult('html/2-level-entry.html', [label: f.translate('general'), childData: handlers.processElements(generalChildren)])
        def groups = party.'gmd:contactInfo'.'*'.'*'

        def half = (int) Math.round((groups.size()) / 2)

        def output = '<div class="row">'
        output += commonHandlers.func.textColEl(general.toString() + handlers.processElements(groups.take(half - 1)), 6)
        output += commonHandlers.func.textColEl(handlers.processElements(groups.drop(half - 1)), 6)
        output += '</div>'

        return handlers.fileResult('html/2-level-entry.html', [label: f.nodeLabel(el), childData: output])
    }

    def bboxEl = {
        el ->
            def replacements = [
                    w: el.'gmd:westBoundLongitude'.'gco:Decimal'.text(),
                    e: el.'gmd:eastBoundLongitude'.'gco:Decimal'.text(),
                    s: el.'gmd:southBoundLatitude'.'gco:Decimal'.text(),
                    n: el.'gmd:northBoundLatitude'.'gco:Decimal'.text()
            ]

            def bboxData = handlers.fileResult("html/bbox.html", replacements)
            return handlers.fileResult('html/2-level-entry.html', [label: f.nodeLabel(el), childData: bboxData])
    }


    def rootPackageEl = {
        el ->
            def rootPackage = el.children().findAll { ch -> !this.packageViews.contains(ch.name()) }
            def otherPackage = el.children().findAll { ch -> this.packageViews.contains(ch.name()) }

            def rootPackageData = handlers.processElements(rootPackage, el);
            def otherPackageData = handlers.processElements(otherPackage, el);

            def rootPackageOutput = handlers.fileResult('html/2-level-entry.html',
                    [label: f.nodeLabel(el), childData: rootPackageData, name: 'gmd_MD_Metadata'])

            return  rootPackageOutput.toString() + otherPackageData
    }
}
