package artifact

import (
	"fmt"
	"io"
	"net/url"

	"github.com/spf13/cobra"

	"github.com/open-banking-labs/dcc/dcc-cli/api"
	"github.com/open-banking-labs/dcc/dcc-cli/internal/cmdutil"
	"github.com/open-banking-labs/dcc/dcc-cli/internal/output"
	"github.com/open-banking-labs/dcc/dcc-cli/pkg/cmd/factory"
)

// NewCmdDDL returns the CREATE DDL command for table structures.
func NewCmdDDL(f *factory.Factory, kind Kind) *cobra.Command {
	var schema string
	var flyway bool
	cmd := &cobra.Command{
		Use:   "ddl <id>",
		Short: "Generate CREATE DDL for one table structure",
		Args:  cobra.ExactArgs(1),
		RunE: func(cmd *cobra.Command, args []string) error {
			id, err := parseID(args)
			if err != nil {
				return err
			}
			out, err := output.FromCommand(cmd)
			if err != nil {
				return err
			}
			client, _, err := cmdutil.NewClient(cmd, f)
			if err != nil {
				return err
			}

			query := url.Values{}
			if schema != "" {
				query.Set("schema", schema)
			}
			var ddl api.TableDdl
			path := fmt.Sprintf("%s/%d/ddl", kind.APIPath, id)
			if err := client.Get(cmd.Context(), path, query, &ddl); err != nil {
				return cmdutil.WrapError(err)
			}
			return out.Print(f.IOStreams.Out, ddl, func(w io.Writer) error {
				if flyway {
					if _, err := fmt.Fprintf(w, "-- %s\n", ddl.FlywayFileName); err != nil {
						return err
					}
					_, err := fmt.Fprintln(w, ddl.FlywayScript)
					return err
				}
				_, err := fmt.Fprintln(w, ddl.DDL)
				return err
			})
		},
	}
	cmd.Flags().StringVar(&schema, "schema", "", "Schema override")
	cmd.Flags().BoolVar(&flyway, "flyway", false, "Print the Flyway migration instead of the plain DDL")
	return cmd
}

// NewCmdDDLAlter returns the ALTER DDL command for table structures.
func NewCmdDDLAlter(f *factory.Factory, kind Kind) *cobra.Command {
	var schema, from, to string
	cmd := &cobra.Command{
		Use:   "ddl-alter <id>",
		Short: "Generate ALTER DDL between two table versions",
		Args:  cobra.ExactArgs(1),
		RunE: func(cmd *cobra.Command, args []string) error {
			id, err := parseID(args)
			if err != nil {
				return err
			}
			out, err := output.FromCommand(cmd)
			if err != nil {
				return err
			}
			client, _, err := cmdutil.NewClient(cmd, f)
			if err != nil {
				return err
			}

			query := url.Values{"from": {from}, "to": {to}}
			if schema != "" {
				query.Set("schema", schema)
			}
			var ddl api.TableDdl
			path := fmt.Sprintf("%s/%d/ddl/alter", kind.APIPath, id)
			if err := client.Get(cmd.Context(), path, query, &ddl); err != nil {
				return cmdutil.WrapError(err)
			}
			return out.Print(f.IOStreams.Out, ddl, func(w io.Writer) error {
				_, err := fmt.Fprintln(w, ddl.DDL)
				return err
			})
		},
	}
	cmd.Flags().StringVar(&schema, "schema", "", "Schema override")
	cmd.Flags().StringVar(&from, "from", "", "Source version")
	cmd.Flags().StringVar(&to, "to", "", "Target version")
	_ = cmd.MarkFlagRequired("from")
	_ = cmd.MarkFlagRequired("to")
	return cmd
}
