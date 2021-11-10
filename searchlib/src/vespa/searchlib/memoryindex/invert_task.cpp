// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "invert_task.h"
#include "document_inverter_context.h"
#include "field_inverter.h"
#include "invert_context.h"
#include "url_field_inverter.h"

namespace search::memoryindex {

using document::Document;
using document::Field;

namespace {

std::unique_ptr<document::FieldValue>
get_field_value(const Document& doc, const std::unique_ptr<const Field>& field)
{
    if (field) {
        return doc.getValue(*field);
    }
    return {};
}

}

InvertTask::InvertTask(const DocumentInverterContext& inv_context, const InvertContext& context, const std::vector<std::unique_ptr<FieldInverter>>& inverters,  const std::vector<std::unique_ptr<UrlFieldInverter>>& uri_inverters, uint32_t lid, const document::Document& doc)
    : _inv_context(inv_context),
      _context(context),
      _inverters(inverters),
      _uri_inverters(uri_inverters),
      _field_values(),
      _uri_field_values(),
      _lid(lid)
{
    _context.set_data_type(_inv_context, doc);
    _field_values.reserve(_context.get_fields().size());
    _uri_field_values.reserve(_context.get_uri_fields().size());
    for (auto& document_field : _context.get_document_fields()) {
        _field_values.emplace_back(get_field_value(doc, document_field));
    }
    for (auto& document_uri_field : _context.get_document_uri_fields()) {
        _uri_field_values.emplace_back(get_field_value(doc, document_uri_field));
    }
}

InvertTask::~InvertTask() = default;

void
InvertTask::run()
{
    assert(_field_values.size() == _context.get_fields().size());
    assert(_uri_field_values.size() == _context.get_uri_fields().size());
    auto fv_itr = _field_values.begin();
    for (auto field_id : _context.get_fields()) {
        _inverters[field_id]->invertField(_lid, *fv_itr);
        ++fv_itr;
    }
    auto uri_fv_itr = _uri_field_values.begin();
    for (auto uri_field_id : _context.get_uri_fields()) {
        _uri_inverters[uri_field_id]->invertField(_lid, *uri_fv_itr);
        ++uri_fv_itr;
    }
}

}
