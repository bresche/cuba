/*
 * Copyright (c) 2010 Haulmont Technology Ltd. All Rights Reserved.
 * Haulmont Technology proprietary and confidential.
 * Use is subject to license terms.

 * Author: Konstantin Krivopustov
 * Created: 13.09.2010 12:15:58
 *
 * $Id$
 */
package com.haulmont.cuba.security.entity;

import com.haulmont.chile.core.annotations.MetaProperty;
import com.haulmont.cuba.core.entity.StandardEntity;
import com.haulmont.cuba.core.entity.annotation.SystemLevel;
import com.haulmont.cuba.core.global.MessageProvider;
import com.haulmont.cuba.core.global.MessageUtils;

import javax.persistence.*;

@Entity(name = "sec$SessionAttribute")
@Table(name = "SEC_SESSION_ATTR")
@SystemLevel
public class SessionAttribute extends StandardEntity {

    private static final long serialVersionUID = 4886168889020578592L;

    @Column(name = "NAME", length = 50)
    private String name;

    @Column(name = "STR_VALUE", length = 1000)
    private String stringValue;

    @Column(name = "DATATYPE", length = 20)
    private String datatype;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "GROUP_ID")
    private Group group;

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getStringValue() {
        return stringValue;
    }

    public void setStringValue(String stringValue) {
        this.stringValue = stringValue;
    }

    public String getDatatype() {
        return datatype;
    }

    public void setDatatype(String datatype) {
        this.datatype = datatype;
    }

    public Group getGroup() {
        return group;
    }

    public void setGroup(Group group) {
        this.group = group;
    }

    @MetaProperty
    public String getDatatypeCaption() {
        return MessageProvider.getMessage(MessageUtils.getMessagePack(), "Datatype." + datatype);
    }
}
