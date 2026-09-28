package cn.org.openbanking.dcc.generator.openapi;

import cn.org.openbanking.dcc.core.interfaceapi.content.InterfaceContent;

/** The metadata needed to place one interface into an OpenAPI document. */
public record InterfaceSpec(String interfaceNo, String name, String url, InterfaceContent content) {
}
