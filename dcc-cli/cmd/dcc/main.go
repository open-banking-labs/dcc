// Command dcc is the DCC command-line client.
package main

import (
	"os"

	"github.com/open-banking-labs/dcc/dcc-cli/pkg/cmd/root"
)

func main() {
	os.Exit(root.Execute())
}
