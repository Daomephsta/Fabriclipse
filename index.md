---
layout: home
title: Fabriclipse | Home
---
<script>
function clipUpdateSiteUrl()
{
    var source = document.getElementById("updateSiteUrl");
    navigator.clipboard.writeText("{{site.url}}{{site.baseurl}}/update_site");
}
</script>
# Installation

1. In Eclipse select **Help** > **Install New Software...**
1. Paste **{{site.url}}{{site.baseurl}}/update_site** in the **Work with** box  
<input id="updateSiteUrl" type="button" onclick="clipUpdateSiteUrl();" 
value="Copy update site URL">
1. Check **Fabriclipse Feature** and click **Next >**
1. Review the installation and click **Next >**
1. Read the license and accept it. If it's not displayed, read it 
[here]({{site.git_repo}}/blob/main/LICENSE). Click **Finish**.
1. A warning about installing unsigned software will appear. This is expected, 
but you should check you're installing what you expect in this kind of situation.
Double-check the update site URL is correct, and be sure you trust the author.
Then click **Install Anyway**.
