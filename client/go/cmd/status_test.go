// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
// status command tests
// Author: bratseth

package cmd

import (
	"testing"

	"github.com/stretchr/testify/assert"
)

func TestStatusDeployCommand(t *testing.T) {
	assertDeployStatus("http://127.0.0.1:19071", []string{}, t)
}

func TestStatusDeployCommandWithURLTarget(t *testing.T) {
	assertDeployStatus("http://mydeploytarget", []string{"-t", "http://mydeploytarget"}, t)
}

func TestStatusDeployCommandWithLocalTarget(t *testing.T) {
	assertDeployStatus("http://127.0.0.1:19071", []string{"-t", "local"}, t)
}

func TestStatusQueryCommand(t *testing.T) {
	assertQueryStatus("http://127.0.0.1:8080", []string{}, t)
}

func TestStatusQueryCommandWithUrlTarget(t *testing.T) {
	assertQueryStatus("http://mycontainertarget", []string{"-t", "http://mycontainertarget"}, t)
}

func TestStatusQueryCommandWithLocalTarget(t *testing.T) {
	assertQueryStatus("http://127.0.0.1:8080", []string{"-t", "local"}, t)
}

func TestStatusDocumentCommandWithLocalTarget(t *testing.T) {
	assertDocumentStatus("http://127.0.0.1:8080", []string{"-t", "local"}, t)
}

func TestStatusErrorResponse(t *testing.T) {
	assertQueryStatusError("http://127.0.0.1:8080", []string{}, t)
}

func assertDeployStatus(target string, args []string, t *testing.T) {
	client := &mockHttpClient{}
	assert.Equal(t,
		"\x1b[32mDeploy API at "+target+" is ready\x1b[0m\n",
		executeCommand(t, client, []string{"status", "deploy"}, args),
		"vespa status config-server")
	assert.Equal(t, target+"/ApplicationStatus", client.lastRequest.URL.String())
}

func assertQueryStatus(target string, args []string, t *testing.T) {
	client := &mockHttpClient{}
	assert.Equal(t,
		"\x1b[32mQuery API at "+target+" is ready\x1b[0m\n",
		executeCommand(t, client, []string{"status", "query"}, args),
		"vespa status container")
	assert.Equal(t, target+"/ApplicationStatus", client.lastRequest.URL.String())

	assert.Equal(t,
		"\x1b[32mQuery API at "+target+" is ready\x1b[0m\n",
		executeCommand(t, client, []string{"status"}, args),
		"vespa status (the default)")
	assert.Equal(t, target+"/ApplicationStatus", client.lastRequest.URL.String())
}

func assertDocumentStatus(target string, args []string, t *testing.T) {
	client := &mockHttpClient{}
	assert.Equal(t,
		"\x1b[32mDocument API at "+target+" is ready\x1b[0m\n",
		executeCommand(t, client, []string{"status", "document"}, args),
		"vespa status container")
	assert.Equal(t, target+"/ApplicationStatus", client.lastRequest.URL.String())
}

func assertQueryStatusError(target string, args []string, t *testing.T) {
	client := &mockHttpClient{nextStatus: 500}
	assert.Equal(t,
		"\x1b[31mQuery API at "+target+" is not ready\x1b[0m\n\x1b[33mStatus 500\x1b[0m\n",
		executeCommand(t, client, []string{"status", "container"}, args),
		"vespa status container")
}
