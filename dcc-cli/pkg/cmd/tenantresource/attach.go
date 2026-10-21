package tenantresource

import (
	"github.com/spf13/cobra"

	"github.com/open-banking-labs/dcc/dcc-cli/pkg/cmd/factory"
)

// Attach adds list/view/create/edit/delete/restore to a noun command.
func Attach(parent *cobra.Command, f *factory.Factory, kind Kind) {
	parent.AddCommand(
		NewCmdList(f, kind),
		NewCmdView(f, kind),
		NewCmdCreate(f, kind),
		NewCmdEdit(f, kind),
		NewCmdDelete(f, kind),
		NewCmdRestore(f, kind),
	)
}
