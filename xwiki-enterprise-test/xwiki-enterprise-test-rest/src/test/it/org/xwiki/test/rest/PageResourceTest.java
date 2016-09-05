/*
 * See the NOTICE file distributed with this work for additional
 * information regarding copyright ownership.
 *
 * This is free software; you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation; either version 2.1 of
 * the License, or (at your option) any later version.
 *
 * This software is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this software; if not, write to the Free
 * Software Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA
 * 02110-1301 USA, or see the FSF site: http://www.fsf.org.
 */
package org.xwiki.test.rest;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

import javax.ws.rs.core.MediaType;

import org.apache.commons.httpclient.HttpStatus;
import org.apache.commons.httpclient.NameValuePair;
import org.apache.commons.httpclient.methods.DeleteMethod;
import org.apache.commons.httpclient.methods.GetMethod;
import org.apache.commons.httpclient.methods.PostMethod;
import org.apache.commons.httpclient.methods.PutMethod;
import org.junit.Assert;
import org.junit.Test;
import org.xwiki.localization.LocaleUtils;
import org.xwiki.rest.Relations;
import org.xwiki.rest.model.jaxb.History;
import org.xwiki.rest.model.jaxb.HistorySummary;
import org.xwiki.rest.model.jaxb.Link;
import org.xwiki.rest.model.jaxb.Object;
import org.xwiki.rest.model.jaxb.Page;
import org.xwiki.rest.model.jaxb.PageSummary;
import org.xwiki.rest.model.jaxb.Pages;
import org.xwiki.rest.model.jaxb.Property;
import org.xwiki.rest.model.jaxb.Space;
import org.xwiki.rest.model.jaxb.Spaces;
import org.xwiki.rest.model.jaxb.Translation;
import org.xwiki.rest.model.jaxb.Wiki;
import org.xwiki.rest.model.jaxb.Wikis;
import org.xwiki.rest.resources.pages.PageChildrenResource;
import org.xwiki.rest.resources.pages.PageHistoryResource;
import org.xwiki.rest.resources.pages.PageResource;
import org.xwiki.rest.resources.pages.PageTranslationResource;
import org.xwiki.rest.resources.wikis.WikisResource;
import org.xwiki.test.rest.framework.AbstractHttpTest;
import org.xwiki.test.rest.framework.TestConstants;
import org.xwiki.test.ui.TestUtils;

import static org.hamcrest.Matchers.isIn;
import static org.junit.Assert.assertThat;

public class PageResourceTest extends AbstractHttpTest
{
    private Page getFirstPage() throws Exception
    {
        GetMethod getMethod = executeGet(getFullUri(WikisResource.class));
        Assert.assertEquals(getHttpMethodInfo(getMethod), HttpStatus.SC_OK, getMethod.getStatusCode());

        Wikis wikis = (Wikis) unmarshaller.unmarshal(getMethod.getResponseBodyAsStream());
        Assert.assertTrue(wikis.getWikis().size() > 0);
        Wiki wiki = wikis.getWikis().get(0);

        // Get a link to an index of spaces (http://localhost:8080/xwiki/rest/wikis/xwiki/spaces)
        Link spacesLink = getFirstLinkByRelation(wiki, Relations.SPACES);
        Assert.assertNotNull(spacesLink);
        getMethod = executeGet(spacesLink.getHref());
        Assert.assertEquals(getHttpMethodInfo(getMethod), HttpStatus.SC_OK, getMethod.getStatusCode());
        Spaces spaces = (Spaces) unmarshaller.unmarshal(getMethod.getResponseBodyAsStream());
        Assert.assertTrue(spaces.getSpaces().size() > 0);

        Space space = null;
        for (final Space s : spaces.getSpaces()) {
            if ("Main".equals(s.getName())) {
                space = s;
                break;
            }
        }

        // get the pages list for the space
        // eg: http://localhost:8080/xwiki/rest/wikis/xwiki/spaces/Main/pages
        Link pagesInSpace = getFirstLinkByRelation(space, Relations.PAGES);
        Assert.assertNotNull(pagesInSpace);
        getMethod = executeGet(pagesInSpace.getHref());
        Assert.assertEquals(getHttpMethodInfo(getMethod), HttpStatus.SC_OK, getMethod.getStatusCode());
        Pages pages = (Pages) unmarshaller.unmarshal(getMethod.getResponseBodyAsStream());
        Assert.assertTrue(pages.getPageSummaries().size() > 0);

        Link pageLink = null;
        for (final PageSummary ps : pages.getPageSummaries()) {
            if ("WebHome".equals(ps.getName())) {
                pageLink = getFirstLinkByRelation(ps, Relations.PAGE);
                Assert.assertNotNull(pageLink);
                break;
            }
        }
        Assert.assertNotNull(pageLink);

        getMethod = executeGet(pageLink.getHref());
        Assert.assertEquals(getHttpMethodInfo(getMethod), HttpStatus.SC_OK, getMethod.getStatusCode());

        Page page = (Page) unmarshaller.unmarshal(getMethod.getResponseBodyAsStream());

        return page;
    }

    @Override
    @Test
    public void testRepresentation() throws Exception
    {
        Page page = getFirstPage();

        Link link = getFirstLinkByRelation(page, Relations.SELF);
        Assert.assertNotNull(link);

        checkLinks(page);
    }

    @Test
    public void testGETNotExistingPage() throws Exception
    {
        GetMethod getMethod =
            executeGet(buildURI(PageResource.class, getWiki(), Arrays.asList("NOTEXISTING"), "NOTEXISTING").toString());
        Assert.assertEquals(getHttpMethodInfo(getMethod), HttpStatus.SC_NOT_FOUND, getMethod.getStatusCode());
    }

    @Test
    public void testPUTGETPage() throws Exception
    {
        final String title = String.format("Title (%s)", UUID.randomUUID().toString());
        final String content = String.format("This is a content (%d)", System.currentTimeMillis());
        final String comment = String.format("Updated title and content (%d)", System.currentTimeMillis());

        Page originalPage = getFirstPage();

        Page newPage = objectFactory.createPage();
        newPage.setTitle(title);
        newPage.setContent(content);
        newPage.setComment(comment);

        Link link = getFirstLinkByRelation(originalPage, Relations.SELF);
        Assert.assertNotNull(link);

        // PUT
        PutMethod putMethod =
            executePutXml(link.getHref(), newPage, TestUtils.ADMIN_CREDENTIALS.getUserName(),
                TestUtils.ADMIN_CREDENTIALS.getPassword());
        Assert.assertEquals(getHttpMethodInfo(putMethod), HttpStatus.SC_ACCEPTED, putMethod.getStatusCode());
        Page modifiedPage = (Page) unmarshaller.unmarshal(putMethod.getResponseBodyAsStream());

        Assert.assertEquals(title, modifiedPage.getTitle());
        Assert.assertEquals(content, modifiedPage.getContent());
        Assert.assertEquals(comment, modifiedPage.getComment());

        // GET
        GetMethod getMethod = executeGet(link.getHref());
        Assert.assertEquals(getHttpMethodInfo(getMethod), HttpStatus.SC_OK, getMethod.getStatusCode());
        modifiedPage = (Page) unmarshaller.unmarshal(getMethod.getResponseBodyAsStream());

        Assert.assertEquals(title, modifiedPage.getTitle());
        Assert.assertEquals(content, modifiedPage.getContent());
        Assert.assertEquals(comment, modifiedPage.getComment());
    }

    @Test
    public void testPUTGETWithObject() throws Exception
    {
        String pageURI = buildURI(PageResource.class, getWiki(), Arrays.asList("RESTTest"), "PageWithObject");

        final String title = String.format("Title (%s)", UUID.randomUUID().toString());
        final String content = String.format("This is a content (%d)", System.currentTimeMillis());
        final String comment = String.format("Updated title and content (%d)", System.currentTimeMillis());

        Page newPage = objectFactory.createPage();
        newPage.setTitle(title);
        newPage.setContent(content);
        newPage.setComment(comment);

        // Add object
        final String TAG_VALUE = "TAG";
        Property property = new Property();
        property.setName("tags");
        property.setValue(TAG_VALUE);
        Object object = objectFactory.createObject();
        object.setClassName("XWiki.TagClass");
        object.getProperties().add(property);
        newPage.setObjects(objectFactory.createObjects());
        newPage.getObjects().getObjectSummaries().add(object);

        // PUT
        PutMethod putMethod =
            executePutXml(pageURI, newPage, TestUtils.ADMIN_CREDENTIALS.getUserName(),
                TestUtils.ADMIN_CREDENTIALS.getPassword());
        assertThat(getHttpMethodInfo(putMethod), putMethod.getStatusCode(),
            isIn(Arrays.asList(HttpStatus.SC_ACCEPTED, HttpStatus.SC_CREATED)));

        Page modifiedPage = (Page) unmarshaller.unmarshal(putMethod.getResponseBodyAsStream());

        Assert.assertEquals(title, modifiedPage.getTitle());
        Assert.assertEquals(content, modifiedPage.getContent());
        Assert.assertEquals(comment, modifiedPage.getComment());

        // GET
        GetMethod getMethod = executeGet(pageURI + "?objects=true");
        Assert.assertEquals(getHttpMethodInfo(getMethod), HttpStatus.SC_OK, getMethod.getStatusCode());
        modifiedPage = (Page) unmarshaller.unmarshal(getMethod.getResponseBodyAsStream());

        Assert.assertEquals(title, modifiedPage.getTitle());
        Assert.assertEquals(content, modifiedPage.getContent());
        Assert.assertEquals(comment, modifiedPage.getComment());

        Assert.assertEquals(TAG_VALUE,
            getProperty((Object) modifiedPage.getObjects().getObjectSummaries().get(0), "tags").getValue());

        // Send again but with empty object list

        modifiedPage.getObjects().getObjectSummaries().clear();

        // PUT
        putMethod =
            executePutXml(pageURI, modifiedPage, TestUtils.ADMIN_CREDENTIALS.getUserName(),
                TestUtils.ADMIN_CREDENTIALS.getPassword());
        assertThat(getHttpMethodInfo(putMethod), putMethod.getStatusCode(), isIn(Arrays.asList(HttpStatus.SC_ACCEPTED)));

        modifiedPage = (Page) unmarshaller.unmarshal(putMethod.getResponseBodyAsStream());

        Assert.assertEquals(title, modifiedPage.getTitle());
        Assert.assertEquals(content, modifiedPage.getContent());
        Assert.assertEquals(comment, modifiedPage.getComment());

        // GET
        getMethod = executeGet(pageURI + "?objects=true");
        Assert.assertEquals(getHttpMethodInfo(getMethod), HttpStatus.SC_OK, getMethod.getStatusCode());
        modifiedPage = (Page) unmarshaller.unmarshal(getMethod.getResponseBodyAsStream());

        Assert.assertEquals(title, modifiedPage.getTitle());
        Assert.assertEquals(content, modifiedPage.getContent());
        Assert.assertEquals(comment, modifiedPage.getComment());

        Assert.assertTrue(modifiedPage.getObjects().getObjectSummaries().isEmpty());
    }

    public Property getProperty(Object object, String propertyName)
    {
        for (Property property : object.getProperties()) {
            if (property.getName().equals(propertyName)) {
                return property;
            }
        }

        return null;
    }

    @Test
    public void testPUTPageWithTextPlain() throws Exception
    {
        final String CONTENT = String.format("This is a content (%d)", System.currentTimeMillis());

        Page originalPage = getFirstPage();

        Link link = getFirstLinkByRelation(originalPage, Relations.SELF);
        Assert.assertNotNull(link);

        PutMethod putMethod =
            executePut(link.getHref(), CONTENT, MediaType.TEXT_PLAIN, TestUtils.ADMIN_CREDENTIALS.getUserName(),
                TestUtils.ADMIN_CREDENTIALS.getPassword());
        Assert.assertEquals(getHttpMethodInfo(putMethod), HttpStatus.SC_ACCEPTED, putMethod.getStatusCode());

        Page modifiedPage = (Page) unmarshaller.unmarshal(putMethod.getResponseBodyAsStream());

        Assert.assertEquals(CONTENT, modifiedPage.getContent());
    }

    @Test
    public void testPUTPageUnauthorized() throws Exception
    {
        Page page = getFirstPage();
        page.setContent("New content");

        Link link = getFirstLinkByRelation(page, Relations.SELF);
        Assert.assertNotNull(link);

        PutMethod putMethod = executePutXml(link.getHref(), page);
        Assert.assertEquals(getHttpMethodInfo(putMethod), HttpStatus.SC_UNAUTHORIZED, putMethod.getStatusCode());
    }

    @Test
    public void testPUTNonExistingPage() throws Exception
    {
        final List<String> SPACE_NAME = Arrays.asList("Test");
        final String PAGE_NAME = String.format("Test-%d", System.currentTimeMillis());
        final String CONTENT = String.format("Content %d", System.currentTimeMillis());
        final String TITLE = String.format("Title %d", System.currentTimeMillis());
        final String PARENT = "Main.WebHome";

        Page page = objectFactory.createPage();
        page.setContent(CONTENT);
        page.setTitle(TITLE);
        page.setParent(PARENT);

        PutMethod putMethod =
            executePutXml(buildURI(PageResource.class, getWiki(), SPACE_NAME, PAGE_NAME).toString(), page,
                TestUtils.ADMIN_CREDENTIALS.getUserName(), TestUtils.ADMIN_CREDENTIALS.getPassword());
        Assert.assertEquals(getHttpMethodInfo(putMethod), HttpStatus.SC_CREATED, putMethod.getStatusCode());

        Page modifiedPage = (Page) unmarshaller.unmarshal(putMethod.getResponseBodyAsStream());

        Assert.assertEquals(CONTENT, modifiedPage.getContent());
        Assert.assertEquals(TITLE, modifiedPage.getTitle());

        Assert.assertEquals(PARENT, modifiedPage.getParent());
    }

    @Test
    public void testPUTWithInvalidRepresentation() throws Exception
    {
        Page page = getFirstPage();
        Link link = getFirstLinkByRelation(page, Relations.SELF);

        PutMethod putMethod =
            executePut(link.getHref(),
                "<?xml version=\"1.0\" encoding=\"UTF-8\"?><invalidPage><content/></invalidPage>", MediaType.TEXT_XML);
        Assert.assertEquals(getHttpMethodInfo(putMethod), HttpStatus.SC_BAD_REQUEST, putMethod.getStatusCode());
    }

    @Test
    public void testPUTGETTranslation() throws Exception
    {
        createPageIfDoesntExist(TestConstants.TEST_SPACE_NAME, TestConstants.TRANSLATIONS_PAGE_NAME, "Translations");

        // PUT
        String[] languages = Locale.getISOLanguages();
        final String languageId = languages[random.nextInt(languages.length)];

        Page page = objectFactory.createPage();
        page.setContent(languageId);

        PutMethod putMethod =
            executePutXml(
                buildURI(PageTranslationResource.class, getWiki(), TestConstants.TEST_SPACE_NAME,
                    TestConstants.TRANSLATIONS_PAGE_NAME, languageId).toString(), page,
                TestUtils.ADMIN_CREDENTIALS.getUserName(), TestUtils.ADMIN_CREDENTIALS.getPassword());
        Assert.assertEquals(getHttpMethodInfo(putMethod), HttpStatus.SC_CREATED, putMethod.getStatusCode());

        // GET
        GetMethod getMethod =
            executeGet(buildURI(PageTranslationResource.class, getWiki(), TestConstants.TEST_SPACE_NAME,
                TestConstants.TRANSLATIONS_PAGE_NAME, languageId).toString());
        Assert.assertEquals(getHttpMethodInfo(getMethod), HttpStatus.SC_OK, getMethod.getStatusCode());

        Page modifiedPage = (Page) unmarshaller.unmarshal(getMethod.getResponseBodyAsStream());

        // Some of the language codes returned by Locale#getISOLanguages() are deprecated and Locale's constructors map
        // the new codes to the old ones which means the language code we have submitted can be different than the
        // actual language code used when the Locale object is created on the server side. Let's go through the Locale
        // constructor to be safe.
        String expectedLanguage = LocaleUtils.toLocale(languageId).getLanguage();
        Assert.assertEquals(expectedLanguage, modifiedPage.getLanguage());
        Assert.assertTrue(modifiedPage.getTranslations().getTranslations().size() > 0);

        for (Translation translation : modifiedPage.getTranslations().getTranslations()) {
            getMethod = executeGet(getFirstLinkByRelation(translation, Relations.PAGE).getHref());
            Assert.assertEquals(getHttpMethodInfo(getMethod), HttpStatus.SC_OK, getMethod.getStatusCode());

            modifiedPage = (Page) unmarshaller.unmarshal(getMethod.getResponseBodyAsStream());

            Assert.assertEquals(modifiedPage.getLanguage(), translation.getLanguage());

            checkLinks(translation);
        }
    }

    @Test
    public void testGETNotExistingTranslation() throws Exception
    {
        createPageIfDoesntExist(TestConstants.TEST_SPACE_NAME, TestConstants.TRANSLATIONS_PAGE_NAME, "Translations");

        GetMethod getMethod =
            executeGet(buildURI(PageResource.class, getWiki(), TestConstants.TEST_SPACE_NAME,
                TestConstants.TRANSLATIONS_PAGE_NAME).toString());
        Assert.assertEquals(getHttpMethodInfo(getMethod), HttpStatus.SC_OK, getMethod.getStatusCode());

        getMethod =
            executeGet(buildURI(PageTranslationResource.class, getWiki(), TestConstants.TEST_SPACE_NAME,
                TestConstants.TRANSLATIONS_PAGE_NAME, "NOT_EXISTING").toString());
        Assert.assertEquals(getHttpMethodInfo(getMethod), HttpStatus.SC_NOT_FOUND, getMethod.getStatusCode());
    }

    @Test
    public void testDELETEPage() throws Exception
    {
        final String pageName = String.format("Test-%d", random.nextLong());

        createPageIfDoesntExist(TestConstants.TEST_SPACE_NAME, pageName, "Test page");

        DeleteMethod deleteMethod =
            executeDelete(buildURI(PageResource.class, getWiki(), TestConstants.TEST_SPACE_NAME, pageName).toString(),
                TestUtils.ADMIN_CREDENTIALS.getUserName(), TestUtils.ADMIN_CREDENTIALS.getPassword());
        Assert.assertEquals(getHttpMethodInfo(deleteMethod), HttpStatus.SC_NO_CONTENT, deleteMethod.getStatusCode());

        GetMethod getMethod =
            executeGet(buildURI(PageResource.class, getWiki(), TestConstants.TEST_SPACE_NAME, pageName).toString());
        Assert.assertEquals(getHttpMethodInfo(getMethod), HttpStatus.SC_NOT_FOUND, getMethod.getStatusCode());
    }

    @Test
    public void testDELETEPageNoRights() throws Exception
    {
        final String pageName = String.format("Test-%d", random.nextLong());

        createPageIfDoesntExist(TestConstants.TEST_SPACE_NAME, pageName, "Test page");

        DeleteMethod deleteMethod =
            executeDelete(buildURI(PageResource.class, getWiki(), TestConstants.TEST_SPACE_NAME, pageName).toString());
        Assert.assertEquals(getHttpMethodInfo(deleteMethod), HttpStatus.SC_UNAUTHORIZED, deleteMethod.getStatusCode());

        GetMethod getMethod =
            executeGet(buildURI(PageResource.class, getWiki(), TestConstants.TEST_SPACE_NAME, pageName).toString());
        Assert.assertEquals(getHttpMethodInfo(getMethod), HttpStatus.SC_OK, getMethod.getStatusCode());
    }

    @Test
    public void testPageHistory() throws Exception
    {
        GetMethod getMethod =
            executeGet(buildURI(PageResource.class, getWiki(), Arrays.asList("Main"), "WebHome").toString());

        Assert.assertEquals(getHttpMethodInfo(getMethod), HttpStatus.SC_OK, getMethod.getStatusCode());

        Page originalPage = (Page) unmarshaller.unmarshal(getMethod.getResponseBodyAsStream());
        Assert.assertEquals("Main", originalPage.getSpace());

        String pageHistoryUri =
            buildURI(PageHistoryResource.class, getWiki(), Arrays.asList("Main"), originalPage.getName()).toString();

        getMethod = executeGet(pageHistoryUri);
        Assert.assertEquals(getHttpMethodInfo(getMethod), HttpStatus.SC_OK, getMethod.getStatusCode());

        History history = (History) unmarshaller.unmarshal(getMethod.getResponseBodyAsStream());

        HistorySummary firstVersion = null;
        for (HistorySummary historySummary : history.getHistorySummaries()) {
            if ("1.1".equals(historySummary.getVersion())) {
                firstVersion = historySummary;
            }

            getMethod = executeGet(getFirstLinkByRelation(historySummary, Relations.PAGE).getHref());
            Assert.assertEquals(getHttpMethodInfo(getMethod), HttpStatus.SC_OK, getMethod.getStatusCode());

            Page page = (Page) unmarshaller.unmarshal(getMethod.getResponseBodyAsStream());

            checkLinks(page);

            for (Translation translation : page.getTranslations().getTranslations()) {
                checkLinks(translation);
            }
        }

        Assert.assertNotNull(firstVersion);
        Assert.assertEquals("Imported from XAR", firstVersion.getComment());
    }

    @Test
    public void testPageTranslationHistory() throws Exception
    {
        String pageHistoryUri =
            buildURI(PageHistoryResource.class, getWiki(), TestConstants.TEST_SPACE_NAME,
                TestConstants.TRANSLATIONS_PAGE_NAME).toString();

        GetMethod getMethod = executeGet(pageHistoryUri);
        Assert.assertEquals(getHttpMethodInfo(getMethod), HttpStatus.SC_OK, getMethod.getStatusCode());

        History history = (History) unmarshaller.unmarshal(getMethod.getResponseBodyAsStream());

        for (HistorySummary historySummary : history.getHistorySummaries()) {
            getMethod = executeGet(getFirstLinkByRelation(historySummary, Relations.PAGE).getHref());
            Assert.assertEquals(getHttpMethodInfo(getMethod), HttpStatus.SC_OK, getMethod.getStatusCode());

            Page page = (Page) unmarshaller.unmarshal(getMethod.getResponseBodyAsStream());

            checkLinks(page);
            checkLinks(page.getTranslations());
        }
    }

    @Test
    public void testGETPageChildren() throws Exception
    {
        GetMethod getMethod =
            executeGet(buildURI(PageChildrenResource.class, getWiki(), Arrays.asList("Main"), "WebHome").toString());
        Assert.assertEquals(getHttpMethodInfo(getMethod), HttpStatus.SC_OK, getMethod.getStatusCode());

        Pages pages = (Pages) unmarshaller.unmarshal(getMethod.getResponseBodyAsStream());
        Assert.assertTrue(pages.getPageSummaries().size() > 0);

        for (PageSummary pageSummary : pages.getPageSummaries()) {
            checkLinks(pageSummary);
        }
    }

    @Test
    public void testPOSTPageFormUrlEncoded() throws Exception
    {
        final String CONTENT = String.format("This is a content (%d)", System.currentTimeMillis());
        final String TITLE = String.format("Title (%s)", UUID.randomUUID().toString());

        Page originalPage = getFirstPage();

        Link link = getFirstLinkByRelation(originalPage, Relations.SELF);
        Assert.assertNotNull(link);

        NameValuePair[] nameValuePairs = new NameValuePair[2];
        nameValuePairs[0] = new NameValuePair("title", TITLE);
        nameValuePairs[1] = new NameValuePair("content", CONTENT);

        PostMethod postMethod =
            executePostForm(String.format("%s?method=PUT", link.getHref()), nameValuePairs,
                TestUtils.ADMIN_CREDENTIALS.getUserName(), TestUtils.ADMIN_CREDENTIALS.getPassword());
        Assert.assertEquals(getHttpMethodInfo(postMethod), HttpStatus.SC_ACCEPTED, postMethod.getStatusCode());

        Page modifiedPage = (Page) unmarshaller.unmarshal(postMethod.getResponseBodyAsStream());

        Assert.assertEquals(CONTENT, modifiedPage.getContent());
        Assert.assertEquals(TITLE, modifiedPage.getTitle());
    }

    @Test
    public void testPUTPageSyntax() throws Exception
    {
        Page originalPage = getFirstPage();

        // Use the plain/1.0 syntax since we are sure that the test page does not already use it.
        String newSyntax = "plain/1.0";

        originalPage.setSyntax(newSyntax);

        Link link = getFirstLinkByRelation(originalPage, Relations.SELF);
        Assert.assertNotNull(link);

        PutMethod putMethod =
            executePutXml(link.getHref(), originalPage, TestUtils.ADMIN_CREDENTIALS.getUserName(),
                TestUtils.ADMIN_CREDENTIALS.getPassword());
        Assert.assertEquals(getHttpMethodInfo(putMethod), HttpStatus.SC_ACCEPTED, putMethod.getStatusCode());

        Page modifiedPage = (Page) unmarshaller.unmarshal(putMethod.getResponseBodyAsStream());

        Assert.assertEquals(newSyntax, modifiedPage.getSyntax());
    }

    @Test
    public void testPageCopyFrom() throws Exception
    {
        Page sourcePage = getFirstPage();

        String targetPageName = "newPageCopiedFrom-" + UUID.randomUUID().toString();
        String targetPageUrl =
            getUriBuilder(PageResource.class).build(getWiki(), TestConstants.TEST_SPACE_NAME, targetPageName)
                .toString();

        String targetPageUrlWithCopyFrom = targetPageUrl + "?copyFrom=" + sourcePage.getId();
        PutMethod putMethod = executePutXml(targetPageUrlWithCopyFrom, sourcePage, "Admin", "admin");

        Assert.assertEquals(getHttpMethodInfo(putMethod), HttpStatus.SC_CREATED, putMethod.getStatusCode());

        Page targetPage = (Page) unmarshaller.unmarshal(putMethod.getResponseBodyAsStream());

        Assert.assertEquals(sourcePage.getTitle(), targetPage.getTitle());
        Assert.assertEquals(sourcePage.getContent(), targetPage.getContent());

        /* clean the created page */
        DeleteMethod deleteMethod = executeDelete(targetPageUrl, "Admin", "admin");

        GetMethod getMethod =
            executeGet(getUriBuilder(PageResource.class)
                .build(getWiki(), TestConstants.TEST_SPACE_NAME, targetPageName).toString());
        Assert.assertEquals(getHttpMethodInfo(getMethod), HttpStatus.SC_NOT_FOUND, getMethod.getStatusCode());
    }

    @Test
    public void testPageCopyFromInConflict() throws Exception
    {
        System.out.println("###begin test for copyFrom in conflict###");
        Page sourcePage = getFirstPage();

        String targetPageName = "newPageCopiedFrom-" + UUID.randomUUID().toString();
        String targetPageUrl =
            getUriBuilder(PageResource.class).build(getWiki(), TestConstants.TEST_SPACE_NAME, targetPageName)
                .toString();

        String targetPageUrlWithCopyFrom = targetPageUrl + "?copyFrom=" + sourcePage.getId();

        PutMethod putMethod = executePutXml(targetPageUrlWithCopyFrom, sourcePage, "Admin", "admin");
        System.out.println("targetPageUrlWithCopyFrom = " + targetPageUrlWithCopyFrom);
        Assert.assertEquals(getHttpMethodInfo(putMethod), HttpStatus.SC_CREATED, putMethod.getStatusCode());

        Page targetPage = (Page) unmarshaller.unmarshal(putMethod.getResponseBodyAsStream());

        Assert.assertEquals(sourcePage.getTitle(), targetPage.getTitle());
        Assert.assertEquals(sourcePage.getContent(), targetPage.getContent());

        /* copy to it again, this time, it should be in conflict and remote server does nothing */
        System.out.println("targetPageUrlWithCopyFrom = " + targetPageUrlWithCopyFrom);
        putMethod = executePutXml(targetPageUrlWithCopyFrom, sourcePage, "Admin", "admin");

        Assert.assertEquals(getHttpMethodInfo(putMethod), HttpStatus.SC_CONFLICT, putMethod.getStatusCode());

        /* clean the created page */
        DeleteMethod deleteMethod = executeDelete(targetPageUrl, "Admin", "admin");

        GetMethod getMethod = executeGet(targetPageUrl);
        Assert.assertEquals(getHttpMethodInfo(getMethod), HttpStatus.SC_NOT_FOUND, getMethod.getStatusCode());
    }

    @Test
    public void testPageMoveFrom() throws Exception
    {
        Page originalPage = getFirstPage();

        /* create a temporary page as the source page */
        String sourcePageName = "newPageCopiedFrom-" + UUID.randomUUID().toString();
        String sourcePageUrl =
            getUriBuilder(PageResource.class).build(getWiki(), TestConstants.TEST_SPACE_NAME, sourcePageName)
                .toString();

        String sourcePageUrlWithCopyFrom = sourcePageUrl + "?copyFrom=" + originalPage.getId();
        PutMethod putMethod = executePutXml(sourcePageUrlWithCopyFrom, originalPage, "Admin", "admin");

        Assert.assertEquals(getHttpMethodInfo(putMethod), HttpStatus.SC_CREATED, putMethod.getStatusCode());

        Page sourcePage = (Page) unmarshaller.unmarshal(putMethod.getResponseBodyAsStream());

        /* create a new page as the target page */
        String targetPageName = "newPageMovedFrom-" + UUID.randomUUID().toString();
        String targetPageUrl =
            getUriBuilder(PageResource.class).build(getWiki(), TestConstants.TEST_SPACE_NAME, targetPageName)
                .toString();

        String targetPageUrlWithMoveFrom = targetPageUrl + "?moveFrom=" + sourcePage.getId();
        putMethod = executePutXml(targetPageUrlWithMoveFrom, sourcePage, "Admin", "admin");

        Assert.assertEquals(getHttpMethodInfo(putMethod), HttpStatus.SC_CREATED, putMethod.getStatusCode());

        Page targetPage = (Page) unmarshaller.unmarshal(putMethod.getResponseBodyAsStream());

        Assert.assertEquals(sourcePage.getTitle(), targetPage.getTitle());
        Assert.assertEquals(sourcePage.getContent(), targetPage.getContent());

        /* the source page should be deleted */
        GetMethod getMethod = executeGet(sourcePageUrl);
        Assert.assertEquals(getHttpMethodInfo(getMethod), HttpStatus.SC_NOT_FOUND, getMethod.getStatusCode());

        /* clean the target page */
        DeleteMethod deleteMethod = executeDelete(targetPageUrl, "Admin", "admin");
    }

    @Test
    public void testPageMoveFromInConflict() throws Exception
    {
        Page originalPage = getFirstPage();

        /* create two temporary pages as the source pages */
        String sourcePageName1 = "newPageCopiedFrom-" + UUID.randomUUID().toString();
        String sourcePageUrl1 =
            getUriBuilder(PageResource.class).build(getWiki(), TestConstants.TEST_SPACE_NAME, sourcePageName1)
                .toString();

        String sourcePageUrlWithCopyFrom1 = sourcePageUrl1 + "?copyFrom=" + originalPage.getId();
        PutMethod putMethod = executePutXml(sourcePageUrlWithCopyFrom1, originalPage, "Admin", "admin");

        Assert.assertEquals(getHttpMethodInfo(putMethod), HttpStatus.SC_CREATED, putMethod.getStatusCode());

        Page sourcePage1 = (Page) unmarshaller.unmarshal(putMethod.getResponseBodyAsStream());

        String sourcePageName2 = "newPageCopiedFrom-" + UUID.randomUUID().toString();
        String sourcePageUrl2 =
            getUriBuilder(PageResource.class).build(getWiki(), TestConstants.TEST_SPACE_NAME, sourcePageName2)
                .toString();

        String sourcePageUrlWithCopyFrom2 = sourcePageUrl2 + "?copyFrom=" + originalPage.getId();
        putMethod = executePutXml(sourcePageUrlWithCopyFrom2, originalPage, "Admin", "admin");

        Assert.assertEquals(getHttpMethodInfo(putMethod), HttpStatus.SC_CREATED, putMethod.getStatusCode());

        Page sourcePage2 = (Page) unmarshaller.unmarshal(putMethod.getResponseBodyAsStream());

        /* create a new page as the target page */
        String targetPageName = "newPageMovedFrom-" + UUID.randomUUID().toString();
        String targetPageUrl =
            getUriBuilder(PageResource.class).build(getWiki(), TestConstants.TEST_SPACE_NAME, targetPageName)
                .toString();

        String targetPageUrlWithMoveFrom1 = targetPageUrl + "?moveFrom=" + sourcePage1.getId();
        String targetPageUrlWithMoveFrom2 = targetPageUrl + "?moveFrom=" + sourcePage2.getId();
        putMethod = executePutXml(targetPageUrlWithMoveFrom1, sourcePage1, "Admin", "admin");

        Assert.assertEquals(getHttpMethodInfo(putMethod), HttpStatus.SC_CREATED, putMethod.getStatusCode());

        Page targetPage = (Page) unmarshaller.unmarshal(putMethod.getResponseBodyAsStream());

        Assert.assertEquals(sourcePage1.getTitle(), targetPage.getTitle());
        Assert.assertEquals(sourcePage1.getContent(), targetPage.getContent());

        /* the source page should be deleted */
        GetMethod getMethod = executeGet(sourcePageUrl1);
        Assert.assertEquals(getHttpMethodInfo(getMethod), HttpStatus.SC_NOT_FOUND, getMethod.getStatusCode());

        /* invoke move operation the second time, as the target page already exists, so nothing happens */
        putMethod = executePutXml(targetPageUrlWithMoveFrom2, sourcePage2, "Admin", "admin");

        Assert.assertEquals(getHttpMethodInfo(putMethod), HttpStatus.SC_CONFLICT, putMethod.getStatusCode());

        /* clean the target page */
        DeleteMethod deleteMethod = executeDelete(targetPageUrl, "Admin", "admin");
    }
}
