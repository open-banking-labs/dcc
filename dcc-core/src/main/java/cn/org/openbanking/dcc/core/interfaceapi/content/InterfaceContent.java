package cn.org.openbanking.dcc.core.interfaceapi.content;

import java.util.List;

/**
 * The versioned content of a business interface: header attributes plus the input
 * and output field trees (which reference data standards or are inline).
 */
public record InterfaceContent(
        String name,
        String businessModule,
        String url,
        String description,
        String shortName,
        List<InterfaceField> input,
        List<InterfaceField> output) {

    public InterfaceContent {
        input = input == null ? List.of() : List.copyOf(input);
        output = output == null ? List.of() : List.copyOf(output);
    }
}
