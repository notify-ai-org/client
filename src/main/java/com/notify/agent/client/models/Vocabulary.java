package com.notify.agent.client.models;

import lombok.Data;
import java.util.ArrayList;
import java.util.List;



@Data
public class Vocabulary {
    
    
    private Long id;

    
    private String term;

    
    private String description;

    
    private String type;

    
    private Vocabulary parent;

    
    private Object currentValue;

    
    private List<Vocabulary> children = new ArrayList<>();

}
