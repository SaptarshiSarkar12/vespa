// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "request.h"
#include <vespa/searchlib/common/transport.h>

namespace search::engine {

Request::Request(RelativeTime relativeTime)
    : _relativeTime(std::move(relativeTime)),
      _timeOfDoom(fastos::TimeStamp(fastos::TimeStamp::FUTURE)),
      queryFlags(0),
      ranking(),
      location(),
      propertiesMap(),
      stackItems(0),
      stackDump(),
      _trace(_relativeTime, 0)
{
}

Request::~Request() = default;

void Request::setTimeout(const fastos::TimeStamp & timeout)
{
    _timeOfDoom = getStartTime() + timeout;
}

fastos::TimeStamp Request::getTimeUsed() const
{
    return _relativeTime.timeSinceDawn();
}

fastos::TimeStamp Request::getTimeLeft() const
{
    return _timeOfDoom - _relativeTime.now();
}

bool
Request::should_drop_sort_data() const
{
    return ((queryFlags & fs4transport::QFLAG_DROP_SORTDATA) != 0);
}

}
