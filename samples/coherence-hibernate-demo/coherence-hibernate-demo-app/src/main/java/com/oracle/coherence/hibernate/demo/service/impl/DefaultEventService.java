/*
 * Copyright (c) 2020, Oracle and/or its affiliates.
 *
 * Licensed under the Universal Permissive License v 1.0 as shown at
 * http://oss.oracle.com/licenses/upl.
 */
package com.oracle.coherence.hibernate.demo.service.impl;

import java.util.Date;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.oracle.coherence.hibernate.demo.dao.EventRepository;
import com.oracle.coherence.hibernate.demo.model.Event;
import com.oracle.coherence.hibernate.demo.service.EventService;

/**
*
* @author Gunnar Hillert
*
*/
@Transactional
@Service
public class DefaultEventService implements EventService {

	@Autowired
	private EventRepository eventRepository;

	@Override
	public Page<Event> listEvents(Pageable pageable) {
		return this.eventRepository.findAll(pageable);
	}

	@Override
	public Long createAndStoreEvent(String title, Date date) {
		final Event event = new Event();
		event.setTitle(title);
		event.setDate(date);

		final Event savedEvent = this.eventRepository.save(event);
		return savedEvent.getId();
	}

	@Override
	public Event getEvent(Long id) {
		return this.eventRepository.findById(id).get();
	}

}
