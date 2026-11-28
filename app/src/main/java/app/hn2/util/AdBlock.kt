package app.hn2.util

// shortcut: a small hand-picked list of the biggest ad and tracker hosts, not EasyList.
// Swap in a bundled hosts file if too many ads get through.
private val adHosts = setOf(
    "doubleclick.net",
    "googlesyndication.com",
    "googleadservices.com",
    "googletagservices.com",
    "googletagmanager.com",
    "google-analytics.com",
    "adservice.google.com",
    "amazon-adsystem.com",
    "adnxs.com",
    "adsrvr.org",
    "advertising.com",
    "criteo.com",
    "criteo.net",
    "taboola.com",
    "outbrain.com",
    "pubmatic.com",
    "rubiconproject.com",
    "openx.net",
    "casalemedia.com",
    "moatads.com",
    "scorecardresearch.com",
    "quantserve.com",
    "chartbeat.com",
    "chartbeat.net",
    "hotjar.com",
    "mixpanel.com",
    "segment.io",
    "segment.com",
    "connect.facebook.net",
    "ads-twitter.com",
    "ads.linkedin.com",
    "bat.bing.com",
    "media.net",
    "yieldmo.com",
    "sharethrough.com",
    "33across.com",
    "smartadserver.com",
    "teads.tv",
    "zemanta.com",
    "revcontent.com",
    "mgid.com",
    "adform.net",
    "bidswitch.net",
    "lijit.com",
    "indexww.com",
    "3lift.com",
    "krxd.net",
    "bluekai.com",
    "demdex.net",
    "omtrdc.net",
    "newrelic.com",
    "nr-data.net",
    "permutive.com",
    "parsely.com",
)

/** True if [host] or any parent domain of it is a known ad or tracker host. */
fun isAdHost(host: String): Boolean {
    var h = host.lowercase()
    while (true) {
        if (h in adHosts) return true
        val dot = h.indexOf('.')
        if (dot < 0) return false
        h = h.substring(dot + 1)
    }
}
