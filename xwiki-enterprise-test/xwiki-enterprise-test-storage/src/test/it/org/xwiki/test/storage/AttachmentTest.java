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
package org.xwiki.test.storage;

import java.util.Map;
import java.util.HashMap;
import java.io.ByteArrayOutputStream;

import org.apache.commons.httpclient.HttpMethod;

import org.junit.Assert;
import org.junit.Ignore;
import org.junit.Test;
import org.xwiki.test.storage.framework.AbstractTest;
import org.xwiki.test.storage.framework.TestUtils;
import org.apache.commons.io.IOUtils;

/**
 * Test saving and downloading of attachments.
 *
 * @version $Id$
 * @since 3.0RC1
 */
public class AttachmentTest extends AbstractTest
{
    private static final String ATTACHMENT_CONTENT = "This is content for a very small attachment.";

    private static final String FILENAME = "littleAttachment.txt";

    /**
     * Tests that XWIKI-5405 remains fixed.
     * This test proves that when an attachment is saved using Document.addAttachment and
     * then Document.save() the attachment is actually persisted to the database.
     */
    @Test
    public void testDocumentAddAttachment() throws Exception
    {
        final String test =
            "{{groovy}}\n"
          // First the attacher.
          + "doc.addAttachment('" + FILENAME + "', '" + ATTACHMENT_CONTENT
          + "'.getBytes('UTF-8'));\n"
          + "doc.saveAsAuthor();"
          // then the validator.
          + "println(xwiki.getDocument(doc.getDocumentReference()).getAttachment('" + FILENAME
          + "').getContentAsString());"
          + "{{/groovy}}";

        // Delete the document if it exists.
        TestUtils.doPost(this.getAddressPrefix() + "delete/Test/Attachment?confirm=1&basicauth=1",
               TestUtils.ADMIN_CREDENTIALS,
               null);

        // Create a document.
        TestUtils.doPost(this.getAddressPrefix() + "save/Test/Attachment",
               TestUtils.ADMIN_CREDENTIALS,
               new HashMap<String, String>() {{
            put("basicauth", "1");
            put("content", test);
        }});

        HttpMethod ret = null;

        // Test getAttachment()
        ret = TestUtils.doPost(this.getAddressPrefix() + "view/Test/Attachment?xpage=plain", null, null);
        Assert.assertEquals("<p>" + ATTACHMENT_CONTENT + "</p>", ret.getResponseBodyAsString());

        // Test downloadAction.
        ret = TestUtils.doPost(this.getAddressPrefix() + "download/Test/Attachment/" + FILENAME, null, null);
        Assert.assertEquals(ATTACHMENT_CONTENT, new String(ret.getResponseBody(), "UTF-8"));
        Assert.assertEquals(200, ret.getStatusCode());

        // Make sure there is exactly 1 version of this attachment.
        ret = TestUtils.doPost(this.getAddressPrefix() + "preview/Test/Attachment?xpage=plain",
                     TestUtils.ADMIN_CREDENTIALS,
                     new HashMap<String, String>() {{
            put("basicauth", "1");
            put("content", "{{velocity}}$doc.getAttachment('"
                           + FILENAME + "').getVersions().size(){{/velocity}}");
        }});
        Assert.assertEquals("<p>1</p>", ret.getResponseBodyAsString());

        // Make sure that version contains the correct content.
        ret = TestUtils.doPost(this.getAddressPrefix() + "preview/Test/Attachment?xpage=plain",
                     TestUtils.ADMIN_CREDENTIALS,
                     new HashMap<String, String>() {{
            put("basicauth", "1");
            put("content", "{{velocity}}$doc.getAttachment('" + FILENAME
                           + "').getAttachmentRevision('1.1').getContentAsString(){{/velocity}}");
        }});
        Assert.assertEquals("<p>" + ATTACHMENT_CONTENT + "</p>", ret.getResponseBodyAsString());
    }

    /**
     * XWIKI-6126
     */
    @Test
    public void testImportDocumentWithAttachment() throws Exception
    {
        final String docName = "/Test/Attachment2";

        final String attachURL = this.getAddressPrefix() + "download" + docName + "/" + FILENAME;

        // Delete the document if it exists.
        TestUtils.doPost(this.getAddressPrefix() + "delete" + docName + "?confirm=1&basicauth=1",
               TestUtils.ADMIN_CREDENTIALS, null);

        HttpMethod ret = null;
        // Upload the XAR to import.
        final ByteArrayOutputStream baos = new ByteArrayOutputStream();
        IOUtils.copy(this.getClass().getResourceAsStream("/Test.Attachment2.xar"), baos);
        ret = TestUtils.doUpload(this.getAddressPrefix() + "upload/XWiki/XWikiPreferences?basicauth=1",
                       TestUtils.ADMIN_CREDENTIALS,
                       new HashMap<String, byte[]>() {{
            put("Test.Attachment2.xar", baos.toByteArray());
        }});

        // Do the import.
        ret = TestUtils.doPost(this.getAddressPrefix() + "import/XWiki/XWikiPreferences",
                     TestUtils.ADMIN_CREDENTIALS,
                     new HashMap<String, String>() {{
            put("basicauth", "1");
            put("action", "import");
            put("name", "Test.Attachment2.xar");
            put("historyStrategy", "add");
            put("pages", "Test.Attachment2");
        }});

        // Check for attachment content.
        Assert.assertEquals(ATTACHMENT_CONTENT, TestUtils.getPageAsString(attachURL));
    }

    @Test
    public void testRollbackAfterDeleteAttachment() throws Exception
    {
        final String docName = "/Test/testRollbackAfterDeleteAttachment";

        final String attachURL = this.getAddressPrefix() + "download" + docName + "/" + FILENAME;

        // Delete the document if it exists.
        TestUtils.doPost(this.getAddressPrefix() + "delete" + docName + "?confirm=1&basicauth=1", TestUtils.ADMIN_CREDENTIALS, null);

        // Create a document.
        TestUtils.doPost(this.getAddressPrefix() + "save" + docName + "?basicauth=1", TestUtils.ADMIN_CREDENTIALS, null);

        HttpMethod ret = null;
        // Upload the attachment
        ret = TestUtils.doUpload(this.getAddressPrefix() + "upload" + docName + "?basicauth=1",
                       TestUtils.ADMIN_CREDENTIALS,
                       new HashMap<String, byte[]>() {{
            put(FILENAME, ATTACHMENT_CONTENT.getBytes());
        }});

        // Make sure it's there.
        Assert.assertEquals(ATTACHMENT_CONTENT, TestUtils.getPageAsString(attachURL));

        // Delete it
        TestUtils.doPost(this.getAddressPrefix() + "delattachment" + docName + "/" + FILENAME + "?basicauth=1",
               TestUtils.ADMIN_CREDENTIALS, null);

        // Make sure it's nolonger there.
        Assert.assertFalse(ATTACHMENT_CONTENT.equals(TestUtils.getPageAsString(attachURL)));

        // Do a rollback.
        TestUtils.doPost(this.getAddressPrefix() + "rollback" + docName + "?rev=2.1&basicauth=1&confirm=1",
               TestUtils.ADMIN_CREDENTIALS,
               null);

        // Make sure the content is back again.
        Assert.assertEquals(ATTACHMENT_CONTENT, TestUtils.getPageAsString(attachURL));
    }

    @Test
    public void testRollbackAfterNewAttachment() throws Exception
    {
        final String docName = "/Test/testRollbackAfterNewAttachment";

        final String attachURL = this.getAddressPrefix() + "download" + docName + "/" + FILENAME;

        // Delete the document if it exists.
        TestUtils.doPost(this.getAddressPrefix() + "delete" + docName + "?confirm=1&basicauth=1", TestUtils.ADMIN_CREDENTIALS, null);

        // Create a document.
        TestUtils.doPost(this.getAddressPrefix() + "save" + docName + "?basicauth=1", TestUtils.ADMIN_CREDENTIALS, null);

        HttpMethod ret = null;
        // Upload the attachment
        ret = TestUtils.doUpload(this.getAddressPrefix() + "upload" + docName + "?basicauth=1",
                       TestUtils.ADMIN_CREDENTIALS,
                       new HashMap<String, byte[]>() {{
            put(FILENAME, ATTACHMENT_CONTENT.getBytes());
        }});

        // Make sure it's there.
        Assert.assertEquals(ATTACHMENT_CONTENT, TestUtils.getPageAsString(attachURL));

        // Do a rollback.
        TestUtils.doPost(this.getAddressPrefix() + "rollback" + docName + "?rev=1.1&basicauth=1&confirm=1",
               TestUtils.ADMIN_CREDENTIALS,
               null);

        // Make sure it's nolonger there.
        ret = TestUtils.doPost(this.getAddressPrefix() + "download" + docName + "/" + FILENAME, null, null);
        Assert.assertFalse(ATTACHMENT_CONTENT.equals(new String(ret.getResponseBody(), "UTF-8")));
        Assert.assertEquals(404, ret.getStatusCode());
    }

    @Test
    public void testRollbackAfterUpdateOfAttachment() throws Exception
    {
        final String docName = "/Test/testRollbackAfterUpdateOfAttachment";

        final String versionTwo = "This is some different content";

        final String attachURL = this.getAddressPrefix() + "download" + docName + "/" + FILENAME;

        // Delete the document if it exists.
        TestUtils.doPost(this.getAddressPrefix() + "delete" + docName + "?confirm=1&basicauth=1", TestUtils.ADMIN_CREDENTIALS, null);

        // Create a document.
        TestUtils.doPost(this.getAddressPrefix() + "save" + docName + "?basicauth=1", TestUtils.ADMIN_CREDENTIALS, null);

        HttpMethod ret = null;
        // Upload the attachment
        ret = TestUtils.doUpload(this.getAddressPrefix() + "upload" + docName + "?basicauth=1",
                       TestUtils.ADMIN_CREDENTIALS,
                       new HashMap<String, byte[]>() {{
            put(FILENAME, ATTACHMENT_CONTENT.getBytes());
        }});

        // Make sure it's there.
        Assert.assertEquals(ATTACHMENT_CONTENT, TestUtils.getPageAsString(attachURL));

        // Overwrite
        ret = TestUtils.doUpload(this.getAddressPrefix() + "upload" + docName + "?basicauth=1",
                       TestUtils.ADMIN_CREDENTIALS,
                       new HashMap<String, byte[]>() {{
            put(FILENAME, versionTwo.getBytes());
        }});

        // Make sure it is now version2
        Assert.assertEquals(versionTwo, TestUtils.getPageAsString(attachURL));

        // Do a rollback.
        TestUtils.doPost(this.getAddressPrefix() + "rollback" + docName + "?rev=2.1&basicauth=1&confirm=1",
               TestUtils.ADMIN_CREDENTIALS,
               null);

        // Make sure it is version1
        Assert.assertEquals(ATTACHMENT_CONTENT, TestUtils.getPageAsString(attachURL));
    }

    /**
     * If the user saves an attachment, then deletes it, then saves another with the same name,
     * then rolls the version back, the new attachment should be trashed and the old attachment should be
     * restored.
     */
    @Test
    // This test flickers. see: http://jira.xwiki.org/jira/browse/XE-934
    @Ignore
    public void testRollbackAfterSaveDeleteSaveAttachment() throws Exception
    {
        final String docName = "/Test/testRollbackAfterSaveDeleteSaveAttachment";

        final String versionTwo = "This is some different content";

        final String attachURL = this.getAddressPrefix() + "download" + docName + "/" + FILENAME;

        // Delete the document if it exists.
        TestUtils.doPost(this.getAddressPrefix() + "delete" + docName + "?confirm=1&basicauth=1", TestUtils.ADMIN_CREDENTIALS, null);

        // Create a document. v1.1
        TestUtils.doPost(this.getAddressPrefix() + "save" + docName + "?basicauth=1", TestUtils.ADMIN_CREDENTIALS, null);

        HttpMethod ret = null;
        // Upload the attachment v2.1
        ret = TestUtils.doUpload(this.getAddressPrefix() + "upload" + docName + "?basicauth=1",
                       TestUtils.ADMIN_CREDENTIALS,
                       new HashMap<String, byte[]>() {{
            put(FILENAME, ATTACHMENT_CONTENT.getBytes());
        }});

        // Make sure it's there.
        Assert.assertEquals(ATTACHMENT_CONTENT, TestUtils.getPageAsString(attachURL));

        // Delete it v3.1
        TestUtils.doPost(this.getAddressPrefix() + "delattachment" + docName + "/" + FILENAME + "?basicauth=1",
               TestUtils.ADMIN_CREDENTIALS, null);

        // Upload again v4.1
        ret = TestUtils.doUpload(this.getAddressPrefix() + "upload" + docName + "?basicauth=1",
                       TestUtils.ADMIN_CREDENTIALS,
                       new HashMap<String, byte[]>() {{
            put(FILENAME, versionTwo.getBytes());
        }});

        // Make sure it's there.
        Assert.assertEquals(versionTwo, TestUtils.getPageAsString(attachURL));

        // Do a rollback. v5.1
        TestUtils.doPost(this.getAddressPrefix() + "rollback" + docName + "?rev=2.1&basicauth=1&confirm=1",
               TestUtils.ADMIN_CREDENTIALS,
               null);

        // Make sure the latest current version is actually v5.1
        ret = TestUtils.doPost(this.getAddressPrefix() + "preview" + docName + "?basicauth=1&xpage=plain",
                     TestUtils.ADMIN_CREDENTIALS,
                     new HashMap<String, String>() {{
            put("content", "{{velocity}}$doc.getVersion(){{/velocity}}");
        }});
        Assert.assertEquals("<p>5.1</p>", new String(ret.getResponseBody(), "UTF-8"));

        // Make sure it is version1
        Assert.assertEquals(ATTACHMENT_CONTENT, TestUtils.getPageAsString(attachURL));

        // Do rollback to version2. v6.1
        TestUtils.doPost(this.getAddressPrefix() + "rollback" + docName + "?rev=4.1&basicauth=1&confirm=1",
               TestUtils.ADMIN_CREDENTIALS,
               null);

        // Make sure it is version2
        Assert.assertEquals(versionTwo, TestUtils.getPageAsString(attachURL));

        // Make sure the latest current version is actually v6.1
        ret = TestUtils.doPost(this.getAddressPrefix() + "preview" + docName + "?basicauth=1&xpage=plain",
                     TestUtils.ADMIN_CREDENTIALS,
                     new HashMap<String, String>() {{
            put("content", "{{velocity}}$doc.getVersion(){{/velocity}}");
        }});
        Assert.assertEquals("<p>6.1</p>", new String(ret.getResponseBody(), "UTF-8"));
    }

    /**
     * Tests that XWIKI-5436 remains fixed.
     * This test proves that when an attachment is saved using Document.addAttachment
     * and then the document is saved a number of times after, the attachment verstion is not incremented.
     * It also checks that XWikiAttachment.isContentDirty() is false unless the attachment has
     * just been modified.
     */
    @Test
    public void testAttachmentContentDirty() throws Exception
    {
        final String test1 =
            "{{groovy}}\n"
          + "doc.addAttachment('" + FILENAME + "', '" + ATTACHMENT_CONTENT +"'.getBytes('UTF-8'));\n"
          + "print(doc.getDocument().getAttachmentList().get(0).isContentDirty());\n"
          + "doc.saveAsAuthor();\n"
          + "print(' ');"
          + "print(doc.getDocument().getAttachmentList().get(0).isContentDirty());\n"
          + "{{/groovy}}\n";

        final String docName = "/Test/testAttachmentContentDirty";

        // Delete the document if it exists.
        TestUtils.doPost(this.getAddressPrefix() + "delete" + docName + "?confirm=1&basicauth=1", TestUtils.ADMIN_CREDENTIALS, null);

        // Create a document.
        TestUtils.doPost(this.getAddressPrefix() + "save" + docName + "?basicauth=1", TestUtils.ADMIN_CREDENTIALS,
               new HashMap<String, String>(){{
            put("content", test1);
        }});

        Assert.assertEquals("<p>true false</p>",
                            TestUtils.getPageAsString(this.getAddressPrefix() + "view" + docName + "?xpage=plain"));


        // Make sure that on load the attach content isn't dirty.
        final String test2 =
            "{{groovy}}print(doc.getDocument().getAttachmentList().get(0).isContentDirty());{{/groovy}}\n";

        TestUtils.doPost(this.getAddressPrefix() + "save" + docName + "?basicauth=1", TestUtils.ADMIN_CREDENTIALS,
               new HashMap<String, String>(){{
            put("content", test2);
        }});
        Assert.assertEquals("<p>false</p>",
                            TestUtils.getPageAsString(this.getAddressPrefix() + "view" + docName + "?xpage=plain"));

        // Make sure the version of the attachment has not been incremented.
        final String test3 =
            "{{groovy}}print(doc.getDocument().getAttachmentList().get(0).getVersion());{{/groovy}}";
        TestUtils.doPost(this.getAddressPrefix() + "save" + docName + "?basicauth=1", TestUtils.ADMIN_CREDENTIALS,
               new HashMap<String, String>(){{
            put("content", test3);
        }});
        Assert.assertEquals("<p>1.1</p>",
                            TestUtils.getPageAsString(this.getAddressPrefix() + "view" + docName + "?xpage=plain"));

        // Reupload the attachment and make sure the version is incremented.
        TestUtils.doPost(this.getAddressPrefix() + "preview" + docName + "?basicauth=1", TestUtils.ADMIN_CREDENTIALS,
               new HashMap<String, String>(){{
            put("content", test1);
        }});

        TestUtils.doPost(this.getAddressPrefix() + "save" + docName + "?basicauth=1", TestUtils.ADMIN_CREDENTIALS,
               new HashMap<String, String>(){{
            put("content", test3);
        }});
        Assert.assertEquals("<p>1.2</p>",
                            TestUtils.getPageAsString(this.getAddressPrefix() + "view" + docName + "?xpage=plain"));
    }
}