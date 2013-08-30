/*
 * Copyright 2005-2010 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.springframework.ldap.samples.article.dao;

import org.springframework.ldap.core.AttributesMapper;
import org.springframework.ldap.core.ContextMapper;
import org.springframework.ldap.core.DirContextAdapter;
import org.springframework.ldap.core.LdapTemplate;
import org.springframework.ldap.filter.EqualsFilter;
import org.springframework.ldap.samples.article.domain.Person;
import org.springframework.ldap.support.LdapNameBuilder;
import org.springframework.ldap.support.LdapUtils;

import javax.naming.Name;
import javax.naming.NamingException;
import javax.naming.directory.Attributes;
import javax.naming.ldap.LdapName;
import java.util.List;

/**
 * Default implementation of PersonDao. This implementation uses
 * DirContextAdapter for managing attribute values. It has been specified in the
 * Spring Context that the DirObjectFactory should be used when creating objects
 * from contexts, which defaults to creating DirContextAdapter objects. This
 * means that we can use a ContextMapper to map from the found contexts to our
 * domain objects. This is especially useful since we in this case have
 * properties in our domain objects that depend on parts of the DN.
 * 
 * We could have worked with Attributes and an AttributesMapper implementation
 * instead, but working with Attributes is a bore and also, working with
 * AttributesMapper objects (or, indeed Attributes) does not give us access to
 * the distinguished name. However, we do use it in one method that only needs a
 * single attribute: {@link #getAllPersonNames()}.
 * 
 * @author Mattias Hellborg Arthursson
 * @author Ulrik Sandberg
 */
public class PersonDaoImpl implements PersonDao {

	private LdapTemplate ldapTemplate;

	/*
	 * @see PersonDao#create(Person)
	 */
	public void create(Person person) {
		Name dn = buildDn(person);
		DirContextAdapter context = new DirContextAdapter(dn);
		mapToContext(person, context);
		ldapTemplate.bind(dn, context, null);
	}

	/*
	 * @see PersonDao#update(Person)
	 */
	public void update(Person person) {
		Name dn = buildDn(person);
		DirContextAdapter context = (DirContextAdapter) ldapTemplate.lookup(dn);
		mapToContext(person, context);
		ldapTemplate.modifyAttributes(dn, context.getModificationItems());
	}

	/*
	 * @see PersonDao#delete(Person)
	 */
	public void delete(Person person) {
		ldapTemplate.unbind(buildDn(person));
	}

	/*
	 * @see PersonDao#getAllPersonNames()
	 */
	public List getAllPersonNames() {
		EqualsFilter filter = new EqualsFilter("objectclass", "person");
		return ldapTemplate.search(LdapUtils.emptyLdapName(), filter.encode(), new AttributesMapper() {
			public Object mapFromAttributes(Attributes attrs) throws NamingException {
				return attrs.get("cn").get();
			}
		});
	}

	/*
	 * @see PersonDao#findAll()
	 */
	public List findAll() {
		EqualsFilter filter = new EqualsFilter("objectclass", "person");
		return ldapTemplate.search(LdapUtils.emptyLdapName(), filter.encode(), getContextMapper());
	}

	/*
	 * @see PersonDao#findByPrimaryKey(java.lang.String, java.lang.String,
	 * java.lang.String)
	 */
	public Person findByPrimaryKey(String country, String company, String fullname) {
		LdapName dn = buildDn(country, company, fullname);
		return (Person) ldapTemplate.lookup(dn, getContextMapper());
	}

	private ContextMapper getContextMapper() {
		return new PersonContextMapper();
	}

	private LdapName buildDn(Person person) {
		return buildDn(person.getCountry(), person.getCompany(), person.getFullName());
	}

	private LdapName buildDn(String country, String company, String fullname) {
        return LdapNameBuilder.newInstance()
                .add("c", country)
                .add("ou", company)
                .add("cn", fullname)
                .build();
	}

	private void mapToContext(Person person, DirContextAdapter context) {
		context.setAttributeValues("objectclass", new String[] { "top", "person" });
		context.setAttributeValue("cn", person.getFullName());
		context.setAttributeValue("sn", person.getLastName());
		context.setAttributeValue("description", person.getDescription());
		context.setAttributeValue("telephoneNumber", person.getPhone());
	}

	/**
	 * Maps from DirContextAdapter to Person objects. A DN for a person will be
	 * of the form <code>cn=[fullname],ou=[company],c=[country]</code>, so
	 * the values of these attributes must be extracted from the DN. For this,
	 * we use the LdapName along with utility methods in LdapUtils.
	 * 
	 * @author Mattias Hellborg Arthursson
	 * @author Ulrik Sandberg
	 */
	private static class PersonContextMapper implements ContextMapper {

		public Object mapFromContext(Object ctx) {
			DirContextAdapter context = (DirContextAdapter) ctx;
			LdapName dn = LdapUtils.newLdapName(context.getDn());
			Person person = new Person();
			person.setCountry(LdapUtils.getStringValue(dn, 0));
			person.setCompany(LdapUtils.getStringValue(dn, 1));
			person.setFullName(context.getStringAttribute("cn"));
			person.setLastName(context.getStringAttribute("sn"));
			person.setDescription(context.getStringAttribute("description"));
			person.setPhone(context.getStringAttribute("telephoneNumber"));

			return person;
		}
	}

	public void setLdapTemplate(LdapTemplate ldapTemplate) {
		this.ldapTemplate = ldapTemplate;
	}
}
