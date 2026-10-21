// Package factory is the dependency-injection seam used by every command. Tests
// replace the function fields with fakes; production leaves them nil so the
// defaults in internal/cmdutil wire the real implementation.
package factory

import (
	"net/http"

	"github.com/open-banking-labs/dcc/dcc-cli/internal/config"
	"github.com/open-banking-labs/dcc/dcc-cli/pkg/iostreams"
)

// Factory builds the collaborators a command needs. Lazy functions keep help
// output and argument errors from touching configuration or the network.
type Factory struct {
	IOStreams  *iostreams.IOStreams
	HttpClient func() (*http.Client, error)
	Config     func() (config.Config, error)
}
