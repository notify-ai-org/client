package com.notify.agent.client.models;

import lombok.Data;
import java.util.List;


@Data
public class CallStack {
    private List<StackFrame> frames;
}
