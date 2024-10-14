package config

import java.net.URI

case class ProConnectConfiguration(
    url: URI,
    clientId: String,
    clientSecret: String,
    tokenEndpoint: String,
    userinfoEndpoint: String,
    loginRedirectUri: URI,
    logoutRedirectUri: URI,
    allowedProviderIds: List[String]
)
